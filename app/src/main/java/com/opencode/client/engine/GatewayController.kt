package com.opencode.client.engine

import android.content.Context
import com.opencode.client.BuildConfig
import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.core.network.Http
import com.opencode.client.core.secure.CredentialStore
import com.opencode.client.data.gateway.GatewayWorkspaceDto
import com.opencode.client.data.gateway.HttpGatewayApi
import com.opencode.client.data.settings.ServerKind
import com.opencode.client.data.settings.ServerProfile
import com.opencode.client.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the zero-setup experience: gateway sign-in, workspace list/provisioning, and turning a
 * workspace into a live OpenCode connection through [ServerController].
 *
 * The gateway is an open, self-hostable contract (docs/GATEWAY.md). Its base URL is injected at
 * build time (BuildConfig.DEFAULT_GATEWAY_URL) or overridden by self-hosters in Settings.
 */
class GatewayController(
    private val settings: SettingsRepository,
    private val credentials: CredentialStore,
    private val serverController: ServerController
) {

    companion object {
        const val TOKEN_KEY = "gateway:token"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val gatewayUrl: String
        get() = settings.value.gatewayUrlOverride?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_GATEWAY_URL

    /** False when no gateway was configured at build time and none overridden — app then guides to demo/self-host. */
    val isConfigured: Boolean get() = gatewayUrl.isNotBlank()

    private fun api(): HttpGatewayApi? =
        gatewayUrl.takeIf { it.isNotBlank() }?.let { HttpGatewayApi(it) }

    sealed interface AuthState {
        data object SignedOut : AuthState
        data object Working : AuthState
        data class SignedIn(val email: String) : AuthState
        data class Error(val message: String, val technical: String?) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState

    data class WorkspaceUi(
        val id: String,
        val name: String,
        val status: String,
        val endpoint: String
    )

    private val _workspaces = MutableStateFlow<List<WorkspaceUi>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceUi>> = _workspaces

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        // Restore a previous session silently if a token exists.
        scope.launch {
            val email = settings.value.gatewayEmail
            val token = credentials.get(TOKEN_KEY)
            _authState.value = if (!email.isNullOrBlank() && !token.isNullOrBlank()) {
                AuthState.SignedIn(email)
            } else AuthState.SignedOut
            if (_authState.value is AuthState.SignedIn) refreshWorkspaces()
        }
    }

    fun signIn(email: String, password: String) = authenticate(email, password, registerIfMissing = false)
    fun signUp(email: String, password: String) = authenticate(email, password, registerIfMissing = true)

    private fun authenticate(email: String, password: String, registerIfMissing: Boolean) {
        val api = api() ?: run {
            _authState.value = AuthState.Error(
                "No gateway configured in this build.",
                "Set OPCODE_GATEWAY_URL at build time or add one under Settings → Developer."
            )
            return
        }
        if (email.isBlank() || password.length < 6) {
            _authState.value = AuthState.Error("Enter your email and a password of 6+ characters.", null)
            return
        }
        _authState.value = AuthState.Working
        scope.launch {
            val primary = if (registerIfMissing) api.register(email, password) else api.login(email, password)

            val result = when (primary) {
                is Outcome.Ok -> primary
                is Outcome.Err -> {
                    // Friendly path: "sign in" falls back to account creation on 404/401-less misses.
                    if (!registerIfMissing && primary.error is AppError.Http &&
                        (primary.error as AppError.Http).code == 404
                    ) {
                        api.register(email, password)
                    } else primary
                }
            }

            when (result) {
                is Outcome.Ok -> {
                    credentials.put(TOKEN_KEY, result.value.token)
                    settings.setGatewaySession(result.value.email ?: email)
                    settings.setOnboarded()
                    _authState.value = AuthState.SignedIn(email.trim().lowercase())
                    refreshWorkspaces()
                }
                is Outcome.Err ->
                    _authState.value = AuthState.Error(result.error.userMessage, result.error.technical)
            }
        }
    }

    fun signOut() {
        scope.launch {
            credentials.remove(TOKEN_KEY)
            settings.setGatewaySession(null)
            _workspaces.value = emptyList()
            serverController.disconnect()
            _authState.value = AuthState.SignedOut
        }
    }

    fun refreshWorkspaces() {
        val token = credentials.get(TOKEN_KEY) ?: return
        val api = api() ?: return
        scope.launch {
            _busy.value = true
            when (val res = api.workspaces(token)) {
                is Outcome.Ok -> _workspaces.value = res.value.map { it.toUi() }
                is Outcome.Err -> if (res.error is AppError.Http && res.error.code == 401) {
                    signOut()
                }
            }
            _busy.value = false
        }
    }

    fun createWorkspace(name: String, onReady: () -> Unit) {
        val token = credentials.get(TOKEN_KEY) ?: return
        val api = api() ?: return
        scope.launch {
            _busy.value = true
            when (val res = api.createWorkspace(token, name.ifBlank { "workspace" })) {
                is Outcome.Ok -> {
                    _workspaces.value = _workspaces.value + res.value.toUi()
                    _busy.value = false
                    open(res.value.toUi(), onReady)
                }
                is Outcome.Err -> {
                    _busy.value = false
                    _authState.value = AuthState.Error(res.error.userMessage, res.error.technical)
                }
            }
        }
    }

    fun deleteWorkspace(id: String) {
        val token = credentials.get(TOKEN_KEY) ?: return
        val api = api() ?: return
        scope.launch {
            api.deleteWorkspace(token, id)
            _workspaces.value = _workspaces.value.filterNot { it.id == id }
        }
    }

    /**
     * Connects the existing OpenCode stack to a workspace endpoint. Everything downstream
     * (SSE, sessions, tools, permissions, diffs…) behaves exactly as with any real server.
     */
    fun open(workspace: WorkspaceUi, onReady: () -> Unit) {
        val profile = ServerProfile(
            id = "ws-${workspace.id}",
            label = workspace.name,
            url = workspace.endpoint,
            kind = ServerKind.CLOUD
        )
        scope.launch {
            when (serverController.connect(profile)) {
                is Outcome.Ok -> {
                    // No directory override: the workspace's opencode serve runs at the
                    // project root already — its default instance IS the user's project.
                    serverController.setProject(null)
                    onReady()
                }
                is Outcome.Err -> Unit // connection state surfaces the failure in UI
            }
        }
    }

    private fun GatewayWorkspaceDto.toUi() = WorkspaceUi(id, name, status, endpoint)
}
