package dev.opencode.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.opencode.android.data.ChatRepository
import dev.opencode.android.data.ProjectRepository
import dev.opencode.android.data.SecureCredentials
import dev.opencode.android.data.SettingsStore
import dev.opencode.android.opencode.OpenCodeClient
import dev.opencode.android.runtime.HealthChecker
import dev.opencode.android.runtime.LogRingBuffer
import dev.opencode.android.runtime.ProotLauncher
import dev.opencode.android.runtime.RuntimeInstaller
import dev.opencode.android.runtime.RuntimeManager
import dev.opencode.android.ui.chat.ChatViewModel
import dev.opencode.android.ui.projects.ProjectsViewModel
import dev.opencode.android.ui.settings.SettingsViewModel

/**
 * Manual dependency graph (single-module app; no framework needed).
 * One instance per process, owned by [OpenCodeApp].
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val logs = LogRingBuffer()

    val installer = RuntimeInstaller(appContext, logs)
    val launcher = ProotLauncher(appContext, installer, logs)
    val healthChecker = HealthChecker(logs)

    val runtimeManager: RuntimeManager = RuntimeManager(
        context = appContext,
        installer = installer,
        launcher = launcher,
        logs = logs,
    )

    val secureCredentials = SecureCredentials(appContext)
    val settingsStore = SettingsStore(appContext)
    val projectRepository = ProjectRepository(appContext)

    val opencodeClient: OpenCodeClient = OpenCodeClient(
        baseUrlProvider = {
            (runtimeManager.state.value as? dev.opencode.android.runtime.RuntimeState.Healthy)?.info?.baseUrl
        },
    )

    fun chatRepository(): ChatRepository = ChatRepository(
        client = opencodeClient,
        baseUrlProvider = {
            (runtimeManager.state.value as? dev.opencode.android.runtime.RuntimeState.Healthy)?.info?.baseUrl
        },
        logs = logs,
    )

    /** Builds the guest config JSON from user settings + resolved model. */
    suspend fun buildConfigContentJson(): String? {
        val s = settingsStore.current()
        val model = if (s.defaultProvider.isNotBlank() && s.defaultModel.isNotBlank()) {
            "${s.defaultProvider}/${s.defaultModel}"
        } else null
        return s.toConfigContentJson(model)
    }

    // ViewModel factories
    fun chatViewModelFactory(projectId: String): ViewModelProvider.Factory =
        ChatViewModel.Factory(this, projectId)

    fun projectsViewModelFactory(): ViewModelProvider.Factory = ProjectsViewModel.Factory(this)

    fun settingsViewModelFactory(): ViewModelProvider.Factory = SettingsViewModel.Factory(this)
}
