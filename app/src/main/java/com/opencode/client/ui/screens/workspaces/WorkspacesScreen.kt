package com.opencode.client.ui.screens.workspaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.opencode.client.engine.GatewayController
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.ConnectionBadge
import com.opencode.client.ui.components.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Home for the zero-setup flow: the user's gateway workspaces. Opening one connects the app to a
 * real OpenCode server running inside that workspace — the user just sees "their project".
 */
class WorkspacesViewModel(private val container: AppContainer) : ViewModel() {

    val workspaces = container.gateway.workspaces
    val busy = container.gateway.busy
    val auth = container.gateway.authState
    val connection = container.serverController.connectionState

    fun refresh() = container.gateway.refreshWorkspaces()
    fun create(name: String, onReady: () -> Unit) = container.gateway.createWorkspace(name, onReady)
    fun delete(id: String) = container.gateway.deleteWorkspace(id)
    fun open(workspace: GatewayController.WorkspaceUi, onReady: () -> Unit) =
        container.gateway.open(workspace, onReady)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(
    container: AppContainer,
    onOpenWorkspace: () -> Unit,
    onSettings: () -> Unit,
    onDeveloperConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: WorkspacesViewModel = viewModel(factory = simpleFactory { WorkspacesViewModel(container) })
    val workspaces by vm.workspaces.collectAsState()
    val busy by vm.busy.collectAsState()
    val connection by vm.connection.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspaces") },
                actions = {
                    ConnectionBadge(state = connection)
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; vm.refresh() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
                        if (container.settings.value.developerMode) {
                            DropdownMenuItem(
                                text = { Text("Connect your computer") },
                                onClick = { menuOpen = false; onDeveloperConnect() }
                            )
                        }
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; container.gateway.signOut() })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "New workspace")
            }
        }
    ) { padding ->
        Box(modifier.padding(padding).fillMaxSize()) {
            when {
                busy && workspaces.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                workspaces.isEmpty() -> Column(Modifier.fillMaxSize()) {
                    EmptyState(
                        title = "Create your first workspace",
                        hint = "A private Linux environment with a real OpenCode agent — bash, git and tools included."
                    )
                }

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(workspaces, key = { it.id }) { ws ->
                        WorkspaceRow(
                            name = ws.name,
                            status = ws.status,
                            onOpen = { vm.open(ws) { onOpenWorkspace() } },
                            onDelete = { vm.delete(ws.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateWorkspaceDialog(
            onCreate = { name -> showCreate = false; vm.create(name) { onOpenWorkspace() } },
            onDismiss = { showCreate = false }
        )
    }
}

@Composable
private fun WorkspaceRow(name: String, status: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                when (status) {
                    "creating" -> "Preparing environment…"
                    "running" -> "Ready"
                    "stopped" -> "Stopped (tap to wake)"
                    else -> status.replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete workspace")
        }
    }
}

@Composable
private fun CreateWorkspaceDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
        title = { Text("New workspace") },
        text = {
            Column {
                Text(
                    "A fresh Linux environment running the real OpenCode agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
