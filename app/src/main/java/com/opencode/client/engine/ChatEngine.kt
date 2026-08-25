package com.opencode.client.engine

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.core.util.Time
import com.opencode.client.data.repo.PromptAttachment
import com.opencode.client.data.repo.SessionsGateway
import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.domain.ModelRef
import com.opencode.client.domain.PermissionRequest
import com.opencode.client.domain.Role
import com.opencode.client.domain.RunStatus
import com.opencode.client.domain.TodoItem
import com.opencode.client.domain.UiMessage
import com.opencode.client.opencode.event.OpenCodeEvent
import com.opencode.client.opencode.longField
import com.opencode.client.opencode.primitive
import com.opencode.client.opencode.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Event-driven per-session state machine.
 *
 * A snapshot is loaded once via REST (messages, todos, statuses); everything after that is applied
 * incrementally from the SSE event stream. All updates are idempotent upserts keyed by stable
 * server IDs, which makes duplicate delivery and reconnect-resync safe by construction.
 */
class ChatEngine(
    val sessionId: String,
    private val sessions: SessionsGateway,
    externalEvents: Flow<OpenCodeEvent>,
    resyncTick: Flow<Unit>,
    private val scope: CoroutineScope
) {

    data class State(
        val sessionId: String,
        val loaded: Boolean = false,
        val loading: Boolean = false,
        val messages: List<UiMessage> = emptyList(),
        val runStatus: RunStatus = RunStatus.Idle,
        val todos: List<TodoItem> = emptyList(),
        val pendingPermission: PermissionRequest? = null,
        val diffs: List<DiffFileInfo> = emptyList(),
        val diffsDirty: Boolean = false,
        /** ID of the optimistic local prompt not yet confirmed by the server. */
        val pendingLocalId: String? = null,
        val error: AppError? = null
    ) {
        val busy: Boolean get() = runStatus != RunStatus.Idle

        val lastAssistantStreaming: Boolean
            get() = messages.lastOrNull { it.role == Role.ASSISTANT }?.isStreaming == true
    }

    private val _state = MutableStateFlow(State(sessionId))
    val state: StateFlow<State> = _state

    private var eventJob: Job? = null
    private var resyncJob: Job? = null

    init {
        eventJob = scope.launch {
            externalEvents.collect { onEvent(it) }
        }
        resyncJob = scope.launch {
            resyncTick.collect { reload(quiet = true) }
        }
    }

    fun dispose() {
        eventJob?.cancel()
        resyncJob?.cancel()
    }

    /** Surface an error into the visible state (null clears it). */
    fun reportError(error: AppError?) {
        _state.value = _state.value.copy(error = error)
    }

    suspend fun load() = reload(quiet = false)

    private suspend fun reload(quiet: Boolean) {
        if (!quiet) _state.value = _state.value.copy(loading = true, error = null)

        when (val messagesResult = sessions.messages(sessionId)) {
            is Outcome.Ok -> _state.value = _state.value.copy(
                loaded = true,
                loading = false,
                messages = messagesResult.value,
                pendingLocalId = null
            )
            is Outcome.Err -> _state.value = _state.value.copy(
                loading = false,
                error = if (_state.value.loaded) _state.value.error else messagesResult.error
            )
        }

        when (val todosResult = sessions.todo(sessionId)) {
            is Outcome.Ok -> _state.value = _state.value.copy(todos = todosResult.value)
            is Outcome.Err -> Unit // optional feature
        }

        when (val statusResult = sessions.statuses()) {
            is Outcome.Ok -> statusResult.value[sessionId]?.let { st ->
                _state.value = _state.value.copy(runStatus = st)
            }
            is Outcome.Err -> Unit
        }
    }

    suspend fun refreshDiffs(force: Boolean = false): List<DiffFileInfo> {
        val s = _state.value
        if (!force && !s.diffsDirty && s.diffs.isNotEmpty()) return s.diffs
        return when (val res = sessions.sessionDiff(sessionId)) {
            is Outcome.Ok -> {
                _state.value = _state.value.copy(diffs = res.value, diffsDirty = false)
                res.value
            }
            is Outcome.Err -> s.diffs
        }
    }

    suspend fun respondToPermission(request: PermissionRequest, response: String) {
        sessions.respondToPermission(request, response)
        if (_state.value.pendingPermission?.id == request.id) {
            _state.value = _state.value.copy(pendingPermission = null)
        }
    }

    suspend fun abort() {
        sessions.abortSession(sessionId)
        markStreamingComplete()
        _state.value = _state.value.copy(runStatus = RunStatus.Idle)
    }

    /**
     * Sends a prompt with an optimistic local user message. The server-confirmed message
     * replaces the local echo once its message.updated event arrives.
     *
     * @return true when the server accepted the request.
     */
    suspend fun send(text: String, attachments: List<PromptAttachment>, model: ModelRef?, agent: String?): Boolean {
        if (text.isBlank() && attachments.isEmpty()) return false
        val localId = "local-${java.util.UUID.randomUUID()}"
        val optimistic = UiMessage(
            id = localId,
            sessionId = sessionId,
            role = Role.USER,
            createdAt = Time.now(),
            parts = listOf(com.opencode.client.domain.TextPartUi("$localId-p0", text))
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + optimistic,
            pendingLocalId = localId,
            runStatus = RunStatus.Busy,
            error = null
        )
        return when (val result = sessions.sendPrompt(sessionId, text, attachments, model, agent)) {
            is Outcome.Ok -> true
            is Outcome.Err -> {
                _state.value = _state.value.copy(
                    messages = _state.value.messages.filterNot { it.id == localId },
                    pendingLocalId = null,
                    runStatus = RunStatus.Idle,
                    error = result.error
                )
                false
            }
        }
    }

    // ------------------------------------------------------------------ event reduction

    internal fun onEvent(event: OpenCodeEvent) {
        val s = _state.value
        when (event) {
            is OpenCodeEvent.SessionStatus ->
                if (event.sessionID == sessionId) {
                    _state.value = s.copy(
                        runStatus = when (event.status.type) {
                            "busy" -> RunStatus.Busy
                            "retry" -> RunStatus.Retrying(
                                event.status.attempt ?: 0,
                                event.status.message,
                                event.status.next ?: 0L
                            )
                            else -> RunStatus.Idle
                        }
                    )
                }

            is OpenCodeEvent.SessionIdle ->
                if (event.sessionID == sessionId) {
                    markStreamingComplete()
                    _state.value = _state.value.copy(runStatus = RunStatus.Idle)
                }

            is OpenCodeEvent.MessageUpdated -> applyMessageInfo(event.info)

            is OpenCodeEvent.MessageRemoved ->
                if (event.sessionID == sessionId) {
                    _state.value = s.copy(messages = s.messages.filterNot { it.id == event.messageID })
                }

            is OpenCodeEvent.MessagePartUpdated ->
                if (event.part.sessionID == sessionId || event.part.sessionID.isBlank()) {
                    applyPartUpdate(event)
                }

            is OpenCodeEvent.MessagePartRemoved ->
                if (event.sessionID == sessionId) {
                    _state.value = s.copy(
                        messages = s.messages.map { m ->
                            m.copy(parts = m.parts.filterNot { p -> p.id == event.partID })
                        }
                    )
                }

            is OpenCodeEvent.PermissionUpdated ->
                if (event.permission.sessionID.isBlank() || event.permission.sessionID == sessionId) {
                    _state.value = s.copy(pendingPermission = event.permission.toDomain())
                }

            is OpenCodeEvent.PermissionReplied ->
                if (s.pendingPermission?.id == event.permissionID) {
                    _state.value = s.copy(pendingPermission = null)
                }

            is OpenCodeEvent.TodoUpdated ->
                if (event.sessionID == sessionId) {
                    _state.value = s.copy(todos = event.todos.map { it.toDomain() })
                }

            is OpenCodeEvent.FileEdited ->
                _state.value = s.copy(diffsDirty = true)

            is OpenCodeEvent.SessionDiff ->
                if (event.sessionID == sessionId) {
                    _state.value = s.copy(
                        diffs = event.diffs.map { it.toDomain() },
                        diffsDirty = false
                    )
                }

            is OpenCodeEvent.SessionError -> {
                if (event.sessionID == null || event.sessionID == sessionId) {
                    if (event.errorName == "MessageAbortedError") {
                        markStreamingComplete()
                        _state.value = _state.value.copy(runStatus = RunStatus.Idle)
                    } else {
                        _state.value = s.copy(
                            error = AppError.Http(500, "${event.errorName.orEmpty()}: ${event.errorMessage.orEmpty()}")
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    private fun markStreamingComplete() {
        _state.value = _state.value.let { st ->
            st.copy(messages = st.messages.map { m ->
                if (m.isStreaming) m.copy(completedAt = Time.now()) else m
            })
        }
    }

    private fun applyMessageInfo(info: kotlinx.serialization.json.JsonObject) {
        val id = info.primitive("id") ?: return
        val sid = info.primitive("sessionID")
        if (sid != null && sid != sessionId) return
        val roleStr = info.primitive("role") ?: return
        val role = if (roleStr == "user") Role.USER else Role.ASSISTANT
        val created = info.longField("time", "created") ?: Time.now()
        val completed = info.longField("time", "completed")

        val st0 = _state.value
        val existing = st0.messages.firstOrNull { it.id == id }
        var messages = st0.messages
        if (existing == null) {
            messages = messages + UiMessage(
                id = id,
                sessionId = sessionId,
                role = role,
                createdAt = created,
                completedAt = completed
            )
        } else if (completed != null && existing.completedAt == null) {
            messages = messages.map { if (it.id == id) it.copy(completedAt = completed) else it }
        }
        // Replace optimistic local echo with the confirmed user message.
        var pending = st0.pendingLocalId
        if (role == Role.USER && pending != null && existing == null) {
            val local = st0.messages.firstOrNull { it.id == pending }
            if (local != null) {
                messages = messages
                    .filterNot { it.id == local.id }
                    .map { if (it.id == id) it.copy(parts = local.parts, createdAt = local.createdAt) else it }
                pending = null
            }
        }
        _state.value = st0.copy(messages = messages, pendingLocalId = pending)
    }

    private fun applyPartUpdate(event: OpenCodeEvent.MessagePartUpdated) {
        val raw = event.part
        val messageId = raw.messageID.ifBlank { return }
        if (raw.type == "snapshot") return // internal bookkeeping

        val newPart: com.opencode.client.domain.MsgPart? = raw.toDomain()

        _state.value = _state.value.let { st0 ->
            var messages = st0.messages
            if (messages.none { it.id == messageId }) {
                messages = messages + UiMessage(
                    id = messageId,
                    sessionId = sessionId,
                    role = Role.ASSISTANT,
                    createdAt = Time.now()
                )
            }
            messages = messages.map { target ->
                if (target.id != messageId) target else {
                    val parts = if (newPart == null) target.parts else {
                        if (target.parts.any { it.id == newPart.id }) {
                            target.parts.map { if (it.id == newPart.id) newPart else it }
                        } else {
                            target.parts + newPart
                        }
                    }
                    target.copy(parts = parts)
                }
            }
            st0.copy(messages = messages)
        }
    }
}
