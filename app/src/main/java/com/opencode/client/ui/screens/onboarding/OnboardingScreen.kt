package com.opencode.client.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.client.AppContainer
import com.opencode.client.engine.GatewayController
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.ErrorBanner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Consumer-style first run: welcome → sign in → done. No URLs, ports, or runtimes.
 * Demo and developer paths exist but never interrupt this flow.
 */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    val auth: StateFlow<GatewayController.AuthState> = container.gateway.authState
    val isGatewayConfigured: Boolean get() = container.gateway.isConfigured

    fun signIn(email: String, password: String) = container.gateway.signIn(email, password)
    fun signUp(email: String, password: String) = container.gateway.signUp(email, password)

    fun startDemo(onDone: () -> Unit) {
        viewModelScope.launch {
            val demo = com.opencode.client.data.settings.ServerProfile(
                id = "demo", label = "Demo", url = "demo://local",
                isDemo = true, kind = com.opencode.client.data.settings.ServerKind.DEMO
            )
            container.serverController.connect(demo)
            container.settings.setActiveServer(demo.id)
            container.settings.setOnboarded()
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onSignedIn: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSignUp by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Icon(
            Icons.Outlined.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("OpenCode", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your agentic coding workspace.\nReal agent. Real tools. Zero setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(36.dp))
        SignInCard(showSignUp, onToggle = { showSignUp = !showSignUp }, container = container)

        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
            Text("Explore the demo")
        }

        Spacer(Modifier.height(28.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                "  Workspaces run a real OpenCode agent with bash, git and MCP — safely isolated for you.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SignInCard(
    signUp: Boolean,
    onToggle: () -> Unit,
    container: AppContainer,
) {
    val vm: OnboardingViewModel = viewModel(factory = simpleFactory { OnboardingViewModel(container) })
    val auth by vm.auth.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        if (!vm.isGatewayConfigured) {
            ErrorBanner(
                message = "This build has no workspace gateway configured.",
                technical = "Self-host the open gateway (docs/GATEWAY.md) and add its URL under Settings → Developer, or explore the demo.",
                modifier = Modifier.fillMaxWidth()
            )
        } else when (val state = auth) {
            is GatewayController.AuthState.Error -> ErrorBanner(
                message = state.message,
                technical = state.technical,
                modifier = Modifier.fillMaxWidth()
            )
            else -> Unit
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        val working = auth is GatewayController.AuthState.Working
        Button(
            onClick = { if (signUp) vm.signUp(email, password) else vm.signIn(email, password) },
            enabled = !working && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (working) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Working…")
            } else {
                Text(if (signUp) "Create account" else "Sign in")
            }
        }
        TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(if (signUp) "Already have an account? Sign in" else "New here? Create an account")
        }
    }
}
