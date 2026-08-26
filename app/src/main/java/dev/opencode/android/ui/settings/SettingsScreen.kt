package dev.opencode.android.ui.settings

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import dev.opencode.android.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsState()

    var showBashAuto by remember { mutableStateOf(settings.allowBashAutoApprove) }
    var showEditAuto by remember { mutableStateOf(settings.allowEditAutoApprove) }
    var showDebug by remember { mutableStateOf(settings.runtimeDebugLogs) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.automirrored.filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Text("Model", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Default model is inferred from provider + model in your session. Set it per-chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Text("API Keys", style = MaterialTheme.typography.titleMedium)
            Text(
                "Keys are stored encrypted with Android Keystore and only injected into the runtime process at startup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.providers) { p ->
                    KeyRow(
                        provider = p,
                        key = vm.getKey(p.id),
                        onChange = { vm.updateProviderKey(p.id, it) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            ToggleRow(
                label = "Auto-approve shell commands",
                desc = "Skips the permission prompt for bash tool calls.",
                checked = showBashAuto,
                onChange = {
                    showBashAuto = it
                    vm.container.settingsStore.setBashAutoApprove(it)
                },
            )
            ToggleRow(
                label = "Auto-approve file edits",
                desc = "Skips the permission prompt for edit tool calls.",
                checked = showEditAuto,
                onChange = {
                    showEditAuto = it
                    vm.container.settingsStore.setEditAutoApprove(it)
                },
            )
            Spacer(Modifier.height(16.dp))

            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            ToggleRow(
                label = "Runtime debug logs",
                desc = "Shows detailed logs from PRoot and OpenCode in Diagnostics.",
                checked = showDebug,
                onChange = {
                    showDebug = it
                    vm.container.settingsStore.setRuntimeDebugLogs(it)
                },
            )
        }
    }
}

@Composable
private fun KeyRow(
    provider: SettingsViewModel.Provider,
    key: String,
    onChange: (String) -> Unit,
) {
    var visible by rememberSaveable(provider.id) { mutableStateOf(false) }
    val displayKey by remember(key) {
        mutableStateOf(if (visible) key else "*".repeat(key.length.coerceAtLeast(8)))
    }
    val textState = rememberSaveable(provider.id + "_text") { mutableStateOf(key) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(provider.name, style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (visible) "Hide" else "Show",
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("sk-…") },
                )
                Button(onClick = { onChange(textState.value) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}