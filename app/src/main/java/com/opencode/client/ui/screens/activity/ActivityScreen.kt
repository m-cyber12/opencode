package com.opencode.client.ui.screens.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.client.AppContainer
import com.opencode.client.core.Outcome
import com.opencode.client.core.util.Time
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Power-user surface: a terminal-flavored live event feed plus a shell runner that executes
 * commands on the user's machine through OpenCode (POST /session/:id/shell).
 */
class ActivityViewModel(
    private val container: AppContainer,
    private val sessionId: String?
) : ViewModel() {

    data class Entry(val atMs: Long, val type: String, val summary: String)

    data class Ui(
        val entries: List<Entry> = emptyList(),
        val paused: Boolean = false,
        val shellOutput: List<String> = emptyList(),
        val runningShell: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    init {
        viewModelScope.launch {
            container.serverController.events.collect { event ->
                if (_ui.value.paused) return@collect
                val entry = Entry(
                    atMs = Time.now(),
                    type = event.type,
                    summary = summarize(event)
                )
                _ui.value = _ui.value.copy(entries = (_ui.value.entries + entry).takeLast(400))
            }
        }
    }

    fun setPaused(paused: Boolean) {
        _ui.value = _ui.value.copy(paused = paused)
    }

    fun runShell(command: String) {
        val session = sessionId ?: return
        if (command.isBlank() || _ui.value.runningShell) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(runningShell = true)
            val header = "$ ${command}"
            when (val res = container.sessionRepo.runShell(session, agent = "build", command = command)) {
                is Outcome.Ok -> {
                    val text = res.value?.parts
                        ?.filterIsInstance<com.opencode.client.domain.TextPartUi>()
                        ?.joinToString("\n") { it.text }
                        .orEmpty()
                        .ifBlank { "(no output)" }
                    _ui.value = _ui.value.copy(
                        runningShell = false,
                        shellOutput = (_ui.value.shellOutput + header + text).takeLast(200)
                    )
                }
                is Outcome.Err -> _ui.value = _ui.value.copy(
                    runningShell = false,
                    shellOutput = (_ui.value.shellOutput + header + "error: ${res.error.userMessage}").takeLast(200)
                )
            }
        }
    }

    private fun summarize(event: com.opencode.client.opencode.event.OpenCodeEvent): String =
        when (event) {
            is com.opencode.client.opencode.event.OpenCodeEvent.SessionStatus ->
                "session=${event.sessionID.take(8)} status=${event.status.type}"
            is com.opencode.client.opencode.event.OpenCodeEvent.SessionIdle ->
                "session=${event.sessionID.take(8)} idle"
            is com.opencode.client.opencode.event.OpenCodeEvent.MessagePartUpdated ->
                "part=${event.part.type}${event.part.tool?.let { t -> " tool=$t" }.orEmpty()}${event.delta?.let { " +${it.length}ch" }.orEmpty()}"
            is com.opencode.client.opencode.event.OpenCodeEvent.MessageUpdated ->
                "message role=${(event.info["role"] as? kotlinx.serialization.json.JsonPrimitive)?.content}"
            is com.opencode.client.opencode.event.OpenCodeEvent.PermissionUpdated ->
                "${event.permission.type}: ${event.permission.title}"
            is com.opencode.client.opencode.event.OpenCodeEvent.FileEdited ->
                event.file
            is com.opencode.client.opencode.event.OpenCodeEvent.TodoUpdated ->
                "${event.todos.size} todos"
            is com.opencode.client.opencode.event.OpenCodeEvent.Unknown ->
                "unhandled event"
            else -> ""
        }
}

/** Advanced activity view - secondary interface; primary experience stays chat-first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    container: AppContainer,
    sessionId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ActivityViewModel = viewModel(
        key = "activity-${sessionId ?: "none"}",
        factory = simpleFactory { ActivityViewModel(container, sessionId) }
    )
    val ui by vm.ui.collectAsState()
    var shellInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Activity", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { vm.setPaused(!ui.paused) }) {
                        Icon(
                            if (ui.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = if (ui.paused) "Resume feed" else "Pause feed"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            Text(
                "Live server events",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.extended.textFaint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Surface(
                color = AppTheme.extended.codeBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    reverseLayout = false
                ) {
                    items(ui.entries.asReversed()) { entry ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                Time.clock(entry.atMs),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                                color = AppTheme.extended.codeComment
                            )
                            Text(
                                "  ${entry.type}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                                color = AppTheme.extended.info,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (entry.summary.isBlank()) "" else "  ${entry.summary}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                                color = AppTheme.extended.codeBase,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                    if (ui.entries.isEmpty()) {
                        item {
                            Text(
                                "Waiting for events…",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                                color = AppTheme.extended.codeComment
                            )
                        }
                    }
                }
            }

            Text(
                "Shell (runs on your machine via OpenCode)",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.extended.textFaint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (sessionId == null) {
                Text(
                    "Open a chat session first to run commands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extended.warning,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                OutlinedTextField(
                    value = shellInput,
                    onValueChange = { shellInput = it },
                    placeholder = { Text("echo hello") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMonospace),
                    enabled = !ui.runningShell,
                    trailingIcon = {
                        IconButton(
                            onClick = { vm.runShell(shellInput.trim()); shellInput = "" },
                            enabled = !ui.runningShell && shellInput.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run command")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Surface(
                    color = AppTheme.extended.codeBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f)
                        .padding(12.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        ui.shellOutput.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                                color = AppTheme.extended.codeBase
                            )
                        }
                    }
                }
            }
        }
    }
}
