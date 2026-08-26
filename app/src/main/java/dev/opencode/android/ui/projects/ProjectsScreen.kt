package dev.opencode.android.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.opencode.android.data.ProjectRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    vm: ProjectsViewModel,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val projects by vm.projects.collectAsState()
    val context = LocalContext.current

    var showCreate by remember { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProjectRepository.ProjectMeta?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectRepository.ProjectMeta?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            vm.importFromUri(uris, context, null) { id -> id?.let(onOpenProject) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.Info, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            Button(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("New project")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            if (projects.isEmpty()) {
                EmptyProjects()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects, key = { it.id }) { p ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Opened ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.lastOpenedAtEpochMs))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    renameTarget = p; renameName = p.name
                                }) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                                }
                                IconButton(onClick = { deleteTarget = p }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                                Button(onClick = { onOpenProject(p.id) }) { Text("Open") }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(vertical = 12.dp),
            ) { Text("Import from files…") }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create project") },
            text = {
                OutlinedTextField(value = createName, onValueChange = { createName = it }, label = { Text("Name") })
            },
            confirmButton = {
                Button(onClick = {
                    vm.create(createName) { id -> onOpenProject(id) }
                    createName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreate = false }) { Text("Cancel") }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(value = renameName, onValueChange = { renameName = it }, label = { Text("Name") })
            },
            confirmButton = {
                Button(onClick = {
                    vm.rename(target.id, renameName)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("All files in this workspace will be removed permanently.") },
            confirmButton = {
                Button(onClick = { vm.delete(target.id); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyProjects() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to OpenCode", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a project to start working with your local AI coding agent.\nEverything runs on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
