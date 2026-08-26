package dev.opencode.android.data

import Api.str
import dev.opencode.android.opencode.Api
import dev.opencode.android.opencode.OpenCodeClient
import dev.opencode.android.opencode.OpenCodeEventStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Render-friendly projection of an OpenCode message + parts. */
data class UiMessage(
    val id: String,
    val role: String,
    val parts: List<JsonObject>,
) {
    val isAssistant get() = role == "assistant"
    val isUser get() = role == "user"
}

/**
 * Session-scoped orchestrator between the Android UI and the local OpenCode server.
 *
 * Live progress comes exclusively from the SSE stream; the blocking prompt POST
 * resolves the final turn. This mirrors the upstream TUI/web clients' behavior.
 */
class ChatRepository(
    private val client: OpenCodeClient,
    private val baseUrlProvider: () -> String?,
    private val logs: dev.opencode.android.runtime.LogRingBuffer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> get() = _messages

    private val _sessions = MutableStateFlow<List<Api.SessionInfo>>(emptyList())
    val sessions: StateFlow<List<Api.SessionInfo>> get() = _sessions

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy

    private val _permission = MutableStateFlow<Api.PermissionRequest?>(null)
    val permission: StateFlow<Api.PermissionRequest?> get() = _permission

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val error: SharedFlow<String> get() = _error

    @Volatile
    var currentSessionId: String? = null
        private set

    var directory: String = "/"
        private set

    private var eventStream: OpenCodeEventStream? = null
    private var promptJobs = mutableMapOf<String, Job>()

    fun bindDirectory(dir: String) {
        directory = dir
        client.directoryHeader = dir
        eventStream?.stop()
        eventStream = baseUrl()?.let { url ->
            OpenCodeEventStream(url, dir, onLog = { logs.append("sse", it) }).also { s ->
                scope.launch { s.events.collect { handleEvent(it) } }
                s.start()
            }
        }
    }

    private fun baseUrl(): String? = baseUrlProvider.invoke()

    suspend fun loadSessions() {
        runCatching { client.listSessions(directory) }
            .onSuccess { _sessions.value = it }
            .onFailure { _error.tryEmit(humanize(it)) }
    }

    suspend fun openOrCreateSession(existingId: String?, createFresh: Boolean = false): Boolean {
        return try {
            val sid = when {
                createFresh -> client.createSession(null).id
                existingId != null -> existingId
                else -> pickLatestSessionId() ?: client.createSession(null).id
            }
            currentSessionId = sid
            reloadMessages()
            true
        } catch (t: Throwable) {
            _error.tryEmit(humanize(t))
            false
        }
    }

    suspend fun newSession(): Boolean = openOrCreateSession(createFresh = true)

    suspend fun selectSession(sessionId: String) {
        currentSessionId = sessionId
        reloadMessages()
    }

    private suspend fun pickLatestSessionId(): String? {
        loadSessions()
        return _sessions.value.firstOrNull()?.id
    }

    suspend fun deleteSession(id: String) {
        runCatching { client.deleteSession(id) }
        if (currentSessionId == id) {
            currentSessionId = null
            _messages.value = emptyList()
        }
        loadSessions()
    }

    suspend fun reloadMessages() {
        val sid = currentSessionId ?: return
        try {
            val msgs = client.messages(sid)
            _messages.value = msgs.map { m ->
                UiMessage(
                    id = m.messageID.ifEmpty { "m_${m.info.hashCode()}" },
                    role = m.info.str("role") ?: "assistant",
                    parts = m.parts.mapNotNull { it as? JsonObject },
                )
            }
        } catch (t: Throwable) {
            _error.tryEmit(humanize(t))
        }
    }

    /** Fire-and-forget prompt; live deltas arrive over SSE. */
    fun send(text: String, providerID: String?, modelID: String, agent: String, attachments: List<OpenCodeClient.Companion.Attachment> = emptyList()) {
        val sid = currentSessionId ?: return
        if (_busy.value) return
        _busy.value = true
        promptJobs[sid] = scope.launch {
            try {
                client.prompt(sid, text, providerID, modelID, agent, attachments)
                // Final authoritative state (covers any missed deltas).
                reloadMessages()
            } catch (t: Throwable) {
                _error.tryEmit(humanize(t))
            } finally {
                _busy.value = false
                promptJobs.remove(sid)
            }
        }
    }

    fun stopGeneration() {
        val sid = currentSessionId ?: return
        scope.launch {
            runCatching { client.abortSession(sid) }
                .onFailure { _error.tryEmit(humanize(it)) }
        }
    }

    fun respondPermission(requestId: String, reply: String) {
        scope.launch {
            runCatching { client.replyPermission(requestId, reply) }
                .onSuccess {
                    if (_permission.value?.id == requestId) _permission.value = null
                    // Re-scan in case more permissions are queued.
                    runCatching { client.pendingPermissions(directory) }
                        .getOrNull()
                        ?.firstOrNull()
                        ?.let { _permission.value = it }
                }
                .onFailure { _error.tryEmit(humanize(it)) }
        }
    }

    suspend fun ensureProjectReady() {
        runCatching {
            // Creates a git repo when missing so OpenCode scopes the project properly.
            client.gitInit()
        }.onFailure { logs.append("chat", "git init skipped: ${it.message}") }
        loadSessions()
    }

    private suspend fun handleEvent(ev: OpenCodeEventStream.Event) {
        val p = ev.properties
        when (ev.type) {
            "message.updated" -> {
                val info = p["info"] as? JsonObject ?: return
                val id = info.str("id") ?: return
                val role = info.str("role") ?: "assistant"
                mutateMessages { list ->
                    val idx = list.indexOfFirst { it.id == id }
                    if (idx >= 0) list.toMutableList().apply { this[idx] = list[idx].copy(role = role) }
                    else list + UiMessage(id, role, emptyList())
                }
            }

            "message.part.updated" -> {
                val part = p["part"] as? JsonObject ?: return
                val mid = p.str("sessionID")
                val partMsgId = part.str("messageID") ?: mid
                val pid = part.str("id") ?: return
                mutateMessages { list ->
                    var msg = list.firstOrNull { it.parts.any { pp -> pp.str("id") == pid } || it.id == partMsgId }
                        ?: UiMessage(id = partMsgId ?: "unknown_$pid", role = "assistant", parts = emptyList())
                    val parts = msg.parts.filterNot { it.str("id") == pid } + part
                    msg = msg.copy(parts = parts.sortedBy { it.str("id") ?: "" })
                    val others = list.filterNot { it.id == msg!!.id }
                    (others + msg!!).sortedBy { it.id }
                }
            }

            "message.part.delta" -> {
                // Deltas are an optimization; full part snapshots already carry text.
            }

            "message.removed" -> {
                val mid = p.str("messageID") ?: return
                mutateMessages { l -> l.filterNot { it.id == mid } }
            }

            "message.part.removed" -> {
                val pid = p.str("partID") ?: return
                mutateMessages { l -> l.mapNotNull { m ->
                    if (m.parts.none { it.str("id") == pid }) m
                    else m.copy(parts = m.parts.filterNot { it.str("id") == pid })
                } }
            }

            "permission.asked" -> {
                val req = Api.PermissionRequest(
                    id = p.str("id") ?: return,
                    sessionID = p.str("sessionID") ?: "",
                    permission = p.str("permission") ?: p.str("type") ?: "tool",
                    patterns = emptyList(),
                    metadata = p["metadata"] as? JsonObject ?: JsonObject(emptyMap()),
                )
                _permission.value = req
            }

            "permission.replied" -> {
                val rid = p.str("requestID") ?: return
                if (_permission.value?.id == rid) _permission.value = null
            }

            "session.error" -> {
                val err = p["error"]
                val msg = (err as? JsonObject)?.str("message") ?: (err as? JsonPrimitive)?.contentOrNull
                _error.tryEmit(msg ?: "Agent reported an unknown error.")
                _busy.value = false
            }

            "session.deleted" -> {
                val sid = (p["info"] as? JsonObject)?.str("id") ?: return
                if (sid == currentSessionId) {
                    currentSessionId = null
                    _messages.value = emptyList()
                }
            }
        }
    }

    private inline fun mutateMessages(block: (List<UiMessage>) -> List<UiMessage>) {
        _messages.value = block(_messages.value)
    }

    private fun humanize(t: Throwable): String = when {
        t.message?.contains("Failed to connect", true) == true ->
            "Cannot reach the local agent. The runtime may still be starting."
        else -> t.message ?: t.javaClass.simpleName
    }

    fun shutdown() {
        eventStream?.stop()
        scope.coroutineContext[Job]?.cancelChildren()
    }
}
