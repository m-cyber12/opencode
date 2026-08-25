package com.opencode.client.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.opencode.client.data.settings.ServerProfile
import com.opencode.client.data.settings.SettingsRepository
import com.opencode.client.data.settings.ThemeMode
import com.opencode.client.domain.ConnectionState
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.ConnectionBadge
import com.opencode.client.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<com.opencode.client.data.settings.AppSettings> get() = container.settings.settings
    val connection = container.serverController.connectionState

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }
    fun setNotifications(v: Boolean) = viewModelScope.launch { container.settings.setNotifications(v) }
    fun setShowReasoning(v: Boolean) = viewModelScope.launch { container.settings.setShowReasoning(v) }
    fun setKeepAlive(v: Boolean) = viewModelScope.launch { container.settings.setKeepAliveService(v) }
    fun setDeveloperMode(v: Boolean) = viewModelScope.launch { container.settings.setDeveloperMode(v) }
    fun setGatewayOverride(url: String?) = viewModelScope.launch { container.settings.setGatewayUrlOverride(url) }
    fun signOut() {
        viewModelScope.launch { container.gateway.signOut() }
    }

    fun saveServer(profile: ServerProfile, password: String?) {
        viewModelScope.launch {
            if (!profile.isDemo && password != null) {
                if (password.isNotBlank()) container.credentialStore.put(profile.id, password)
                else container.credentialStore.remove(profile.id)
            }
            container.settings.addOrUpdateServer(profile)
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            container.settings.removeServer(id)
            container.credentialStore.remove(id)
            if (container.serverController.activeProfile.value?.id == id) {
                container.serverController.disconnect()
            }
        }
    }

    fun activate(profile: ServerProfile, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = container.serverController.connect(profile)) {
                is com.opencode.client.core.Outcome.Ok -> {
                    container.settings.setActiveServer(profile.id)
                    onResult(null)
                }
                is com.opencode.client.core.Outcome.Err -> onResult(res.error.userMessage)
            }
        }
    }

    fun serverPassword(id: String): String? = container.credentialStore.get(id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel(factory = simpleFactory { SettingsViewModel(container) })
    val settings by vm.settings.collectAsState()
    val connection by vm.connection.collectAsState()

    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {

            // ---- Connection ------------------------------------------------------
            item { SectionHeader("Connection") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (connection) {
                            is ConnectionState.Connected -> "Connected to ${vm.settings.value.servers.firstOrNull { it.id == settings.activeServerId }?.label ?: "server"}"
                            is ConnectionState.Connecting -> "Connecting…"
                            is ConnectionState.Reconnecting -> "Reconnecting…"
                            is ConnectionState.Failed -> "Connection failed"
                            ConnectionState.Disconnected -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    ConnectionBadge(state = connection)
                }
            }
            if ((connection as? ConnectionState.Connected)?.version != null) {
                item {
                    Text(
                        "OpenCode version ${(connection as ConnectionState.Connected).version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.extended.textFaint,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ---- Account (cloud) ----------------------------------------------------
            if (settings.gatewayEmail != null) {
                item { SectionHeader("Account") }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(settings.gatewayEmail ?: "", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Workspace gateway account",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.extended.textFaint
                            )
                        }
                        TextButton(onClick = vm::signOut) { Text("Sign out") }
                    }
                }
            }

            // ---- Developer (hidden unless enabled) -----------------------------------
            if (!settings.developerMode) {
                item {
                    ToggleRow(
                        title = "Developer mode",
                        subtitle = "Manual servers, gateway override, raw diagnostics",
                        checked = false,
                        onChange = { vm.setDeveloperMode(true) }
                    )
                }
            } else {
                item { SectionHeader("Developer · Servers") }
                items(settings.servers.size, key = { settings.servers[it].id }) { idx ->
                    val server = settings.servers[idx]
                    val active = server.id == settings.activeServerId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !active) { vm.activate(server) { err -> connectError = err } }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = active, onClick = { vm.activate(server) { err -> connectError = err } })
                        Column(Modifier.weight(1f)) {
                            Text(server.label + if (server.isDemo) "  (demo)" else "", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.extended.textFaint
                            )
                        }
                        IconButton(onClick = { editing = server }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${server.label}")
                        }
                        IconButton(onClick = { vm.deleteServer(server.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete ${server.label}")
                        }
                    }
                }
                item {
                    TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Connect your own computer")
                    }
                }
                item { SectionHeader("Developer · Gateway") }
                item {
                    GatewayOverrideRow(
                        current = settings.gatewayUrlOverride.orEmpty(),
                        onSave = vm::setGatewayOverride
                    )
                }
                connectError?.let {
                    item {
                        com.opencode.client.ui.components.ErrorBanner(
                            message = it,
                            onDismiss = { connectError = null },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // ---- Appearance -------------------------------------------------------
            item { SectionHeader("Appearance") }
            items(ThemeMode.entries.size) { idx ->
                val mode = ThemeMode.entries[idx]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setTheme(mode) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = { vm.setTheme(mode) })
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "Follow system"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ---- Behavior ----------------------------------------------------------
            item { SectionHeader("Behavior") }
            item {
                ToggleRow(
                    title = "Agent completion notices",
                    subtitle = "Notify while the app runs in the background",
                    checked = settings.notificationsEnabled,
                    onChange = vm::setNotifications
                )
            }
            item {
                ToggleRow(
                    title = "Show agent thinking",
                    subtitle = "Display reasoning summaries above answers",
                    checked = settings.showReasoning,
                    onChange = vm::setShowReasoning
                )
            }
            item {
                ToggleRow(
                    title = "Keep working when I leave the app",
                    subtitle = "Shows a quiet status so long tasks keep streaming (Android may still stop it)",
                    checked = settings.keepAliveServiceEnabled,
                    onChange = vm::setKeepAlive
                )
            }
            if (settings.developerMode) {
                item {
                    ToggleRow(
                        title = "Developer mode",
                        subtitle = "Hide manual server controls again",
                        checked = true,
                        onChange = { vm.setDeveloperMode(false) }
                    )
                }
            }

            // ---- Security -----------------------------------------------------------
            item { SectionHeader("Security") }
            item {
                Text(
                    buildString {
                        append("Credentials are stored in Android Keystore-encrypted preferences and excluded from backups. ")
                        append("Plain-HTTP connections are allowed for LAN use but flagged before first use. TLS verification is never disabled.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extended.textFaint,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                Text(
                    "OpenCode for Android · client v1.0.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.extended.textFaint,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (creating || editing != null) {
        ServerEditorDialog(
            existing = editing,
            initialPassword = editing?.let { vm.serverPassword(it.id) }.orEmpty(),
            onSave = { profile, password ->
                vm.saveServer(profile, password)
                creating = false
                editing = null
            },
            onDismiss = {
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun GatewayOverrideRow(current: String, onSave: (String?) -> Unit) {
    var value by remember { mutableStateOf(current) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Self-hosting the gateway? Point the app at your deployment:",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.extended.textFaint
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text("https://my-gateway.example.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSave(value.trim().ifBlank { null }) }) { Text("Save") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = AppTheme.extended.textFaint)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ServerEditorDialog(
    existing: ServerProfile?,
    initialPassword: String,
    onSave: (ServerProfile, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username ?: "opencode") }
    var password by remember { mutableStateOf(initialPassword) }

    val urlOk = url.contains('.') || url.contains(":") || url.startsWith("http")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("http://192.168.1.20:4096") },
                    singleLine = true,
                    isError = !urlOk
                )
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ServerProfile(
                            id = existing?.id ?: SettingsRepository.newServerId(),
                            label = label.ifBlank { "Server" },
                            url = ServerNormalize(url),
                            username = username.ifBlank { "opencode" },
                            isDemo = existing?.isDemo ?: false
                        ),
                        password
                    )
                },
                enabled = url.isNotBlank() && urlOk
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun ServerNormalize(raw: String): String =
    runCatching { com.opencode.client.engine.ServerController.normalizeUrl(raw) }.getOrDefault(raw)
