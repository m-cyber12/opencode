package com.opencode.client.ui.screens.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.client.AppContainer
import com.opencode.client.core.Outcome
import com.opencode.client.domain.ProjectInfo
import com.opencode.client.domain.SessionInfo
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.ConnectionBadge
import com.opencode.client.ui.components.EmptyState
import com.opencode.client.ui.components.ErrorBanner
import com.opencode.client.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel(private val container: AppContainer) : ViewModel() {

    data class Ui(
        val loading: Boolean = true,
        val projects: List<ProjectInfo> = emptyList(),
        val error: Pair<String, String?>? = null,
        val connection: com.opencode.client.domain.ConnectionState =
            com.opencode.client.domain.ConnectionState.Connecting
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    init {
        viewModelScope.launch {
            container.serverController.connectionState.collect { st ->
                _ui.value = _ui.value.copy(connection = st)
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            when (val res = container.projectRepo.projects()) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(loading = false, projects = res.value)
                is Outcome.Err -> _ui.value = _ui.value.copy(
                    loading = false,
                    error = (res.error.userMessage to res.error.technical)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    container: AppContainer,
    onOpenProject: (ProjectInfo) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ProjectsViewModel = viewModel(factory = simpleFactory { ProjectsViewModel(container) })
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    ConnectionBadge(state = ui.connection, onClick = onSettings)
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier.padding(padding).fillMaxSize()) {
            when {
                ui.loading -> Row(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }

                ui.error != null -> ErrorBanner(
                    message = ui.error!!.first,
                    technical = ui.error!!.second,
                    actionLabel = "Retry",
                    onAction = vm::load,
                    modifier = Modifier.padding(16.dp)
                )

                ui.projects.isEmpty() -> EmptyState(
                    title = "No projects yet",
                    hint = "Start `opencode` inside a repository on your computer; it will appear here."
                )

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ui.projects, key = { it.id }) { project ->
                        ProjectCard(project, onClick = { onOpenProject(project) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectInfo, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    project.worktree,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.extended.textFaint
                )
                if (project.vcs != null) {
                    Text(
                        "${project.vcs} repository",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.extended.info
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open project")
        }
    }
}
