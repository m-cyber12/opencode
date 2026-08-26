package dev.opencode.android.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.opencode.android.AppContainer
import dev.opencode.android.data.ProjectRepository
import dev.opencode.android.data.UiMessage
import dev.opencode.android.opencode.Api
import dev.opencode.android.opencode.OpenCodeClient
import dev.opencode.android.runtime.RuntimePhase
import dev.opencode.android.runtime.RuntimeService
import dev.opencode.android.runtime.RuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatViewModel(
    private val container: AppContainer,
    val projectId: String,
) : ViewModel() {

    private val repo = container.chatRepository()
    private val projects = container.projectRepository

    private val _projectMeta = MutableStateFlow(projects.get(projectId))
    val projectMeta: StateFlow<ProjectRepository.ProjectMeta?> = _projectMeta

    val runtimeState = container.runtimeManager.state
    val messages = repo.messages
    val busy = repo.busy
    val permission = repo.permission
    val sessions = repo.sessions
    val settings = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, dev.opencode.android.data.Settings())

    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> get() = _errors

    init {
        viewModelScope.launch {
            projects.touch(projectId)
            _projectMeta.value = projects.get(projectId)
            RuntimeService.start(container.appContext, projectId)
            // Wait for a healthy runtime before binding the SSE stream.
            awaitHealthy()
            repo.bindDirectory(hostProjectDir().absolutePath)
            repo.ensureProjectReady()
            repo.openOrCreateSession(null)
        }
        viewModelScope.launch {
            repo.error.collect { _errors.value = it }
        }
    }

    fun dismissError() {
        _errors.value = null
    }

    private suspend fun awaitHealthy() {
        val deadline = System.currentTimeMillis() + 150_000
        while (System.currentTimeMillis() < deadline) {
            val st = runtimeState.value
            if (st is RuntimeState.Healthy) return
            if (st is RuntimeState.Failed) return
            kotlinx.coroutines.delay(500)
        }
    }

    fun projectDir(): File = projects.projectDir(projectId)

    fun hostProjectDir(): File = projects.projectDir(projectId)

    fun send(text: String) {
        val s = settings.value
        viewModelScope.launch {
            repo.send(
                text = text,
                providerID = s.defaultProvider.takeIf { it.isNotBlank() },
                modelID = s.defaultModel.ifBlank { DEFAULT_MODEL },
                agent = s.defaultAgent.ifBlank { "build" },
            )
        }
    }

    fun stop() = repo.stopGeneration()

    fun newChat() {
        viewModelScope.launch { repo.newSession() }
    }

    fun selectSession(id: String) {
        viewModelScope.launch { repo.selectSession(id) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { repo.deleteSession(id) }
    }

    fun respondPermission(requestId: String, reply: String) {
        repo.respondPermission(requestId, reply)
    }

    /** Copies a SAF-picked document into the workspace and attaches it. */
    fun attach(context: Context, uri: Uri, filename: String, mime: String) {
        viewModelScope.launch {
            val destDir = File(hostProjectDir(), ".attachments").apply { mkdirs() }
            val dest = withContext(Dispatchers.IO) {
                File(destDir, "${System.currentTimeMillis()}_$filename").also { out ->
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                }
            } ?: run {
                _errors.value = "Could not read the selected file."
                return@launch
            }
            pendingAttachments.add(
                OpenCodeClient.Companion.Attachment(
                    mime = mime.ifBlank { "application/octet-stream" },
                    filename = filename,
                    url = "file://${dest.absolutePath}",
                ),
            )
        }
    }

    val pendingAttachments = mutableListOf<OpenCodeClient.Companion.Attachment>()

    override fun onCleared() {
        repo.shutdown()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-4-5"

        class Factory(
            private val container: AppContainer,
            private val projectId: String,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(container, projectId) as T
        }
    }
}

fun RuntimeState?.isHealthy(): Boolean = this is RuntimeState.Healthy && phase == RuntimePhase.HEALTHY
