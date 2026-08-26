package dev.opencode.android.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.opencode.android.AppContainer
import dev.opencode.android.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel(private val container: AppContainer) : ViewModel() {
    private val _projects = MutableStateFlow(container.projectRepository.list())
    val projects: StateFlow<List<ProjectRepository.ProjectMeta>> get() = _projects

    val onboardingComplete = container.settingsStore.settings

    fun create(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val meta = container.projectRepository.create(name.ifBlank { "Untitled project" })
            container.projectRepository.ensureStarterFile(meta)
            refresh()
            onCreated(meta.id)
        }
    }

    fun rename(id: String, name: String) {
        container.projectRepository.rename(id, name)
        refresh()
    }

    fun delete(id: String) {
        container.projectRepository.delete(id)
        refresh()
    }

    fun importFromUri(uris: List<android.net.Uri>, context: android.content.Context, name: String?, onDone: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val meta = container.projectRepository.create(name ?: "Imported ${System.currentTimeMillis()}")
                val dest = container.projectRepository.projectDir(meta.id)
                uris.forEach { uri ->
                    copyTree(context, uri, dest)
                }
                refresh()
                onDone(meta.id)
            } catch (t: Throwable) {
                onDone(null)
            }
        }
    }

    private fun copyTree(context: android.content.Context, treeUri: android.net.Uri, destRoot: java.io.File) {
        // Copy documents from a SAF directory picker via DocumentFile traversal.
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return
        copyDocRecursive(context, doc, destRoot)
    }

    private fun copyDocRecursive(context: android.content.Context, doc: androidx.documentfile.provider.DocumentFile, destDir: java.io.File) {
        destDir.mkdirs()
        doc.listFiles().forEach { child ->
            if (child.isDirectory) {
                copyDocRecursive(context, child, java.io.File(destDir, child.name ?: "dir"))
            } else {
                child.name?.let { name ->
                    runCatching {
                        val out = java.io.File(destDir, name)
                        context.contentResolver.openInputStream(child.uri)?.use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }
    }

    fun markOnboardingComplete() {
        viewModelScope.launch { container.settingsStore.setOnboardingComplete() }
    }

    fun runtimeState() = container.runtimeManager.state

    fun startRuntime(projectId: String?) {
        dev.opencode.android.runtime.RuntimeService.start(container.appContext, projectId)
    }

    private fun refresh() {
        _projects.value = container.projectRepository.list()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectsViewModel(container) as T
    }
}
