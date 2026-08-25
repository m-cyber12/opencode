package com.opencode.client.engine

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.core.network.AuthInterceptor
import com.opencode.client.core.network.Http
import com.opencode.client.data.settings.ServerProfile
import com.opencode.client.domain.Capabilities
import com.opencode.client.domain.ConnectionState
import com.opencode.client.domain.RunStatus
import com.opencode.client.opencode.HttpOpenCodeApi
import com.opencode.client.opencode.OpenCodeApi
import com.opencode.client.opencode.event.OpenCodeEvent
import com.opencode.client.opencode.event.ParsedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the connection to one OpenCode server: API client, live event transport, capability
 * detection, session run-status tracking and reconnection resync signalling.
 *
 * Everything downstream (repositories, chat engines, screens) reads from here.
 */
class ServerController(
    private val credentialProvider: (ServerProfile) -> String?,
    /** Bearer token source for gateway-managed (cloud) connections. */
    private val bearerProvider: (ServerProfile) -> String? = { null }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _api = MutableStateFlow<OpenCodeApi?>(null)
    /** Current API handle; null while disconnected. */
    val api: OpenCodeApi? get() = _api.value

    private val _activeProfile = MutableStateFlow<ServerProfile?>(null)
    val activeProfile: StateFlow<ServerProfile?> = _activeProfile

    @Volatile
    var activeDirectory: String? = null
        private set

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _events = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 512)
    /** All parsed server events, hot. */
    val events: SharedFlow<OpenCodeEvent> = _events

    private val _resyncTick = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 8)
    /** Emitted after the event stream re-establishes; subscribers should refetch snapshots. */
    val resyncTick: SharedFlow<Unit> = _resyncTick

    private val _busySessions = MutableStateFlow<Set<String>>(emptySet())
    val busySessions: StateFlow<Set<String>> = _busySessions

    private var transport: EventTransport? = null
    private var transportCollector: Job? = null
    private var stateCollector: Job? = null
    private var capabilitiesJob: Job? = null
    private var everLive = false

    // ------------------------------------------------------------------ connect / disconnect

    suspend fun connect(profile: ServerProfile): Outcome<Unit> {
        disconnect()
        everLive = false
        _activeProfile.value = profile
        _connectionState.value = ConnectionState.Connecting

        if (profile.isDemo || profile.kind == com.opencode.client.data.settings.ServerKind.DEMO) {
            val demo = com.opencode.client.demo.DemoApi()
            _api.value = demo
            attachTransport(com.opencode.client.demo.DemoEventTransport(scope), "demo")
            return Outcome.Ok(Unit)
        }

        // Cloud workspaces must never ride plain HTTP.
        if (profile.kind == com.opencode.client.data.settings.ServerKind.CLOUD &&
            !normalizeUrl(profile.url).startsWith("https://")
        ) {
            val err = AppError.Network(
                "workspace endpoint must use https",
                IllegalStateException("insecure cloud endpoint: ${normalizeUrl(profile.url)}")
            )
            _connectionState.value = ConnectionState.Failed(err.userMessage, err.technical)
            return Outcome.Err(err)
        }

        val password = credentialProvider(profile)
        val token = bearerProvider(profile)
        val auth = AuthInterceptor(
            usernameProvider = { profile.username },
            passwordProvider = { password },
            bearerTokenProvider = { token }
        )
        val client = Http.client(auth)
        val realApi = HttpOpenCodeApi.forServer(normalizeUrl(profile.url), client)

        // Health gate: fail fast with a friendly error before anything else runs.
        return when (val health = realApi.health()) {
            is Outcome.Err -> {
                _connectionState.value =
                    ConnectionState.Failed(health.error.userMessage, health.error.technical)
                _api.value = null
                Outcome.Err(health.error)
            }
            is Outcome.Ok -> {
                if (!health.value.healthy) {
                    val err = AppError.Network("server reported unhealthy", IllegalStateException("unhealthy"))
                    _connectionState.value = ConnectionState.Failed(err.userMessage, err.technical)
                    return Outcome.Err(err)
                }
                _api.value = realApi
                attachTransport(
                    SseEventTransport(
                        url = normalizeUrl(profile.url).trimEnd('/') + "/global/event",
                        client = client,
                        scope = scope
                    ),
                    health.value.version
                )
                Outcome.Ok(Unit)
            }
        }
    }

    fun setProject(worktree: String?) {
        activeDirectory = worktree?.takeIf { it.isNotBlank() }
        refreshStatuses()
    }

    fun disconnect() {
        transportCollector?.cancel(); transportCollector = null
        stateCollector?.cancel(); stateCollector = null
        capabilitiesJob?.cancel(); capabilitiesJob = null
        transport?.stop(); transport = null
        _api.value = null
        _busySessions.value = emptySet()
        _connectionState.value = ConnectionState.Disconnected
    }

    // ------------------------------------------------------------------ internals

    private fun attachTransport(newTransport: EventTransport, version: String) {
        transport = newTransport
        markConnected(version)

        stateCollector = scope.launch {
            newTransport.state.collect { s ->
                when (s) {
                    is EventTransport.TransportState.Live -> {
                        if (!everLive) {
                            everLive = true
                        } else {
                            // Reconnected after a drop: force snapshot resynchronization.
                            _resyncTick.tryEmit(Unit)
                        }
                        val current = _connectionState.value
                        if (current !is ConnectionState.Connected) markConnected(version)
                    }
                    is EventTransport.TransportState.Retrying ->
                        _connectionState.value = ConnectionState.Reconnecting(s.attempt, s.nextInMs)
                    else -> Unit
                }
            }
        }

        transportCollector = scope.launch {
            newTransport.frames.collect { data -> dispatchFrame(data) }
        }

        capabilitiesJob = scope.launch {
            val caps = probeCapabilities()
            val current = _connectionState.value
            if (current is ConnectionState.Connected && current.version == version) {
                _connectionState.value = current.copy(capabilities = caps)
            }
        }

        refreshStatuses()
    }

    private fun markConnected(version: String) {
        val caps = (_connectionState.value as? ConnectionState.Connected)?.capabilities ?: Capabilities()
        _connectionState.value = ConnectionState.Connected(version, activeDirectory, caps)
    }

    internal fun dispatchFrame(data: String) {
        val parsed: ParsedFrame = OpenCodeEvent.parse(data)
        if (parsed.event !is OpenCodeEvent.Ignored || parsed.event.type != "<empty>") {
            emit(parsed.event)
        }
    }

    private fun emit(event: OpenCodeEvent) {
        trackBusy(event)
        _events.tryEmit(event)
    }

    private fun trackBusy(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.SessionStatus -> {
                val busy = when (event.status.type) {
                    "busy", "retry" -> true
                    else -> false
                }
                updateBusy(event.sessionID, busy)
            }
            is OpenCodeEvent.SessionIdle -> updateBusy(event.sessionID, false)
            else -> Unit
        }
    }

    private fun updateBusy(sessionId: String, busy: Boolean) {
        val current = _busySessions.value
        val updated = if (busy) current + sessionId else current - sessionId
        if (updated != current) _busySessions.value = updated
    }

    private fun refreshStatuses() {
        val api = _api.value ?: return
        scope.launch {
            when (val res = api.sessionStatuses(activeDirectory)) {
                is Outcome.Ok -> _busySessions.value = res.value
                    .filterValues { it.type == "busy" || it.type == "retry" }
                    .keys
                is Outcome.Err -> Unit // statuses are optional; UI degrades
            }
        }
    }

    private suspend fun probeCapabilities(): Capabilities {
        val api = _api.value ?: return Capabilities()
        suspend fun <T> ok(block: suspend () -> Outcome<T>): Boolean = block() is Outcome.Ok
        return Capabilities(
            supportsProviders = ok { api.providers(activeDirectory) },
            supportsAgents = ok { api.agents(activeDirectory) },
            supportsCommands = ok { api.commands() },
            supportsFiles = ok { api.listFiles(".", activeDirectory) },
            supportsMcp = ok { api.mcpStatus(activeDirectory) },
            supportsVcs = ok { api.vcs(activeDirectory) }
        )
    }

    companion object {
        fun normalizeUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            return url
        }

        fun isSecure(url: String): Boolean = normalizeUrl(url).startsWith("https://")
    }
}
