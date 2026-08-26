package dev.opencode.android.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opencode.android.runtime.RuntimePhase
import dev.opencode.android.runtime.RuntimeState

/**
 * First-run experience (spec §22): the runtime initializes automatically,
 * extraction progress is shown, and the user only ever sees friendly copy.
 */
@Composable
fun WelcomeScreen(
    runtimeState: RuntimeState,
    onReady: () -> Unit,
    onCreateProject: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("OpenCode", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your local AI coding agent.\nEverything runs on this device — nothing to install, nowhere to sign in.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        when (runtimeState.phase) {
            RuntimePhase.IDLE -> {
                LaunchedInit(onCreateProject)
            }
            RuntimePhase.EXTRACTING -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing the on-device agent… ${((runtimeState as? RuntimeState.Extracting)?.progress ?: 0f.let { 0f } * 100).toInt()}%")
                Text("One-time setup.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RuntimePhase.STARTING, RuntimePhase.WAITING_HEALTH, RuntimePhase.VALIDATING -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Starting the local agent…")
            }
            RuntimePhase.HEALTHY -> {
                Button(onClick = onCreateProject, modifier = Modifier.fillMaxWidth()) {
                    Text("Get started")
                }
            }
            RuntimePhase.CRASHED -> {
                if ((runtimeState as? RuntimeState.Crashed)?.willRestart == true) {
                    CircularProgressIndicator(); Text("Restarting the agent…")
                } else {
                    ErrorPane(runtimeState.detail ?: "Unknown error", onRetry = onCreateProject)
                }
            }
            RuntimePhase.FAILED -> ErrorPane((runtimeState as? RuntimeState.Failed)?.detail ?: "", onRetry = onCreateProject)
            else -> {
                Button(onClick = onCreateProject, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun LaunchedInit(onCreateProject: () -> Unit) {
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {}
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Text(message, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
}
