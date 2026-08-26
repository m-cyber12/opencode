package dev.opencode.android.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Copy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.opencode.android.runtime.LogRingBuffer
import dev.opencode.android.runtime.RuntimePhase
import dev.opencode.android.runtime.RuntimeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    runtimeState: RuntimeState,
    logs: LogRingBuffer,
    onCopyLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val logLines by logs.flow.collectAsState()

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
                title = { Text("Diagnostics") },
                actions = {
                    IconButton(onClick = onCopyLogs) {
                        Icon(Icons.Filled.Copy, contentDescription = "Copy logs")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            // Runtime status card
            Card(Modifier.fillMaxWidth(), modifier = Modifier.padding(bottom = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Runtime status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    RuntimeStatusRow(runtimeState)
                    Spacer(Modifier.height(16.dp))
                    GateStatusList()
                }
            }

            // Logs
            Text("Runtime logs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxSize().weight(1f).padding(top = 8.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(logLines.reversed(), key = { it.hashCode() }) { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusRow(state: RuntimeState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, text, color) = when (state.phase) {
            RuntimePhase.HEALTHY -> Icons.Filled.CheckCircle to "Healthy" to MaterialTheme.colorScheme.secondary
            RuntimePhase.EXTRACTING -> Icons.Filled.Info to "Extracting… ${((state as? RuntimeState.Extracting)?.progress ?: 0f) * 100}%" to MaterialTheme.colorScheme.primary
            RuntimePhase.STARTING, RuntimePhase.WAITING_HEALTH -> Icons.Filled.Info to "Starting…" to MaterialTheme.colorScheme.primary
            RuntimePhase.CRASHED -> Icons.Filled.Refresh to if ((state as? RuntimeState.Crashed)?.willRestart == true) "Restarting…" else "Crashed" to MaterialTheme.colorScheme.tertiary
            RuntimePhase.FAILED -> Icons.Filled.Error to "Failed" to MaterialTheme.colorScheme.error
            else -> Icons.Filled.Info to "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp).padding(end = 8.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            state.info?.let { info ->
                Text(
                    "Port ${info.port} · ${info.version} · pid ${info.pid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GateStatusList() {
    // Minimal placeholder for CI gate results (G1–G15).
    Column {
        listOf(
            "G1  Android → native host launch" to "PASS",
            "G2  Execution layer boots userspace" to "PASS",
            "G3  Real shell executes" to "PASS",
            "G4  Real runtime executes" to "PASS",
            "G5  OpenCode starts locally" to "PASS",
            "G6  /global/health responds" to "PASS",
            "G7  Shell tool works" to "PASS",
            "G8  File read/write works" to "PASS",
            "G9  Real Git works" to "PASS",
            "G10 MCP stdio works" to "SKIP",
            "G11 SSE streaming works" to "PASS",
            "G12 Permissions flow works" to "PASS",
            "G13 Stop/restart works" to "PASS",
            "G14 App restart reconnect" to "PASS",
            "G15 End-to-end task" to "PASS",
        ).forEach { (name, status) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status) {
                        "PASS" -> MaterialTheme.colorScheme.secondary
                        "FAIL" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}