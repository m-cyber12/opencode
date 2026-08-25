package com.opencode.client.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.client.AppContainer
import com.opencode.client.data.settings.ServerProfile
import com.opencode.client.data.settings.SettingsRepository
import com.opencode.client.domain.ConnectionState
import com.opencode.client.engine.ServerController
import com.opencode.client.ui.components.ErrorBanner
import com.opencode.client.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    data class Ui(
        val url: String = "",
        val label: String = "",
        val username: String = "opencode",
        val password: String = "",
        val showAdvanced: Boolean = false,
        val testing: Boolean = false,
        val connectedVersion: String? = null,
        val error: Pair<String, String?>? = null,
        val insecureWarningVisible: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    private val controller: ServerController get() = container.serverController
    private val settings: SettingsRepository get() = container.settings

    fun onUrlChange(v: String) {
        _ui.value = _ui.value.copy(
            url = v,
            error = null,
            insecureWarningVisible = ServerController.normalizeUrl(v).startsWith("http://") && v.isNotBlank()
        )
    }

    fun onLabelChange(v: String) { _ui.value = _ui.value.copy(label = v) }
    fun onUsernameChange(v: String) { _ui.value = _ui.value.copy(username = v) }
    fun onPasswordChange(v: String) { _ui.value = _ui.value.copy(password = v) }
    fun toggleAdvanced() { _ui.value = _ui.value.copy(showAdvanced = !_ui.value.showAdvanced) }

    fun acknowledgeInsecure(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.acknowledgeInsecureHttp()
            onDone()
        }
    }

    fun connect(onSuccess: () -> Unit) {
        if (_ui.value.testing) return
        _ui.value = _ui.value.copy(testing = true, error = null)
        viewModelScope.launch {
            val profileId = existingProfileIdFor(_ui.value.url)
            val profile = ServerProfile(
                id = profileId ?: SettingsRepository.newServerId(),
                label = _ui.value.label.ifBlank { hostOf(_ui.value.url) },
                url = ServerController.normalizeUrl(_ui.value.url),
                username = _ui.value.username.ifBlank { "opencode" }
            )
            if (_ui.value.password.isNotBlank()) {
                try {
                    container.credentialStore.put(profile.id, _ui.value.password)
                } catch (e: Exception) {
                    _ui.value = _ui.value.copy(
                        testing = false,
                        error = ("Secure storage is unavailable on this device; credentials were NOT saved." to e.message)
                    )
                    return@launch
                }
            } else {
                container.credentialStore.remove(profile.id)
            }

            when (val result = controller.connect(profile)) {
                is com.opencode.client.core.Outcome.Ok -> {
                    settings.addOrUpdateServer(profile)
                    settings.setActiveServer(profile.id)
                    settings.setOnboarded()
                    _ui.value = _ui.value.copy(testing = false)
                    onSuccess()
                }
                is com.opencode.client.core.Outcome.Err -> {
                    _ui.value = _ui.value.copy(
                        testing = false,
                        error = (result.error.userMessage to result.error.technical)
                    )
                }
            }
        }
    }

    fun connectDemo(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val demo = ServerProfile(
                id = "demo",
                label = "Demo",
                url = "demo://local",
                isDemo = true
            )
            controller.connect(demo)
            settings.setActiveServer(demo.id)
            settings.setOnboarded()
            onSuccess()
        }
    }

    private fun existingProfileIdFor(rawUrl: String): String? {
        val normalized = ServerController.normalizeUrl(rawUrl)
        return settings.value.servers.firstOrNull { it.url == normalized }?.id
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(ServerController.normalizeUrl(url)).host }.getOrNull() ?: url
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    container: AppContainer,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ConnectViewModel = viewModel(factory = simpleFactory { ConnectViewModel(container) })
    val ui by vm.ui.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        Icon(
            Icons.Outlined.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("OpenCode", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your agent. Your machine.\nNow in your pocket.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.extended.textFaint,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = ui.url,
            onValueChange = vm::onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.20:4096") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        if (ui.showAdvanced) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ui.label,
                onValueChange = vm::onLabelChange,
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ui.username,
                onValueChange = vm::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::onPasswordChange,
                label = { Text("Server password (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (ui.insecureWarningVisible && !container.settings.value.insecureHttpAcknowledged) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(
                message = "Plain HTTP sends credentials unencrypted over the network.",
                technical = "Use HTTPS or an SSH tunnel for anything beyond a trusted LAN.",
                actionLabel = "Got it",
                onAction = { vm.acknowledgeInsecure {} }
            )
        }

        ui.error?.let { (msg, tech) ->
            Spacer(Modifier.height(12.dp))
            ErrorBanner(message = msg, technical = tech)
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.connect(onConnected) },
                enabled = !ui.testing && ui.url.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (ui.testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Connecting…")
                } else {
                    Text("Connect")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = { vm.toggleAdvanced() }) {
                Text(if (ui.showAdvanced) "Hide options" else "More options")
            }
            OutlinedButton(onClick = { vm.connectDemo(onConnected) }, enabled = !ui.testing) {
                Text("Try the demo")
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Run `opencode serve` on your computer, then connect to its address from this device. Credentials are stored in Android's Keystore-encrypted storage and never logged.",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.extended.textFaint
        )
        Spacer(Modifier.height(40.dp))
    }
}
