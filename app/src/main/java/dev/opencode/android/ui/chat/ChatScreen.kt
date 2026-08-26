package dev.opencode.android.ui.chat

import Api.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.opencode.android.data.UiMessage
import dev.opencode.android.opencode.OpenCodeEventStream
import dev.opencode.android.runtime.RuntimePhase
import dev.opencode.android.runtime.RuntimeState
import dev.opencode.android.ui.components.DiffCard
import dev.opencode.android.ui.components.ReasoningCard
import dev.opencode.android.ui.components.ToolCallCard
import dev.opencode.android.ui.markdown.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val permission by vm.permission.collectAsState()
    val runtime by vm.runtimeState.collectAsState()
    val projectMeta by vm.projectMeta.collectAsState()
    val error by vm.errors.collectAsState()
    val sessions by vm.sessions.collectAsState()

    var showSessions by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.parts?.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "attachment"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            vm.attach(context, uri, name, mime)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Projects")
                    }
                },
                title = {
                    Column {
                        Text(projectMeta?.name ?: "Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        RuntimeStatusLine(runtime)
                    }
                },
                actions = {
                    IconButton(onClick = { showSessions = true }) {
                        Icon(Icons.Filled.History, contentDescription = "Sessions")
                    }
                    IconButton(onClick = vm::newChat) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                permission?.let { req ->
                    PermissionCard(
                        request = req,
                        onReply = { reply -> vm.respondPermission(req.id, reply) },
                    )
                }
                Composer(
                    value = draft,
                    enabled = true,
                    busy = busy,
                    onValueChange = { draft = it },
                    onSend = {
                        if (draft.isNotBlank()) {
                            vm.send(draft.trim())
                            draft = ""
                        }
                    },
                    onStop = vm::stop,
                    onAttach = { filePicker.launch(arrayOf("*/*")) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (val rt = runtime) {
                is RuntimeState.Healthy -> {}
                is RuntimeState.Extracting -> RuntimeBanner("Preparing local agent… ${(rt.progress * 100).toInt()}%")
                is RuntimeState.Starting -> RuntimeBanner(rt.detail ?: "Starting local agent…")
                is RuntimeState.WaitingHealth -> RuntimeBanner("Waking the local agent…")
                is RuntimeState.Crashed -> RuntimeBanner(
                    if (rt.willRestart) "Agent hiccup — restarting automatically…" else "The local agent stopped: ${rt.detail}",
                )
                is RuntimeState.Failed -> RuntimeBanner(rt.detail, isError = true)
                else -> RuntimeBanner("Initializing…")
            }

            error?.let { msg ->
                ErrorBanner(msg) { vm.dismissError() }
            }

            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id.ifEmpty { "m${it.hashCode()}" } }) { msg ->
                        MessageBubble(msg)
                    }
                    if (busy && permission == null) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.widthIn(min = 8.dp))
                                Text("Working…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSessions) {
        ModalBottomSheet(onDismissRequest = { showSessions = false }) {
            SessionList(
                sessions = sessions.map { s -> s.id to (s.title ?: "Untitled") },
                currentId = null,
                onSelect = { id -> vm.selectSession(id); showSessions = false },
                onDelete = { id -> vm.deleteSession(id) },
                onNew = { vm.newChat(); showSessions = false },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

@Composable
private fun RuntimeStatusLine(state: RuntimeState) {
    val text = when (state.phase) {
        RuntimePhase.HEALTHY -> "● agent online"
        RuntimePhase.WAITING_HEALTH, RuntimePhase.STARTING -> "○ starting…"
        RuntimePhase.EXTRACTING -> "○ preparing runtime…"
        RuntimePhase.CRASHED -> "▲ restarting…"
        RuntimePhase.FAILED -> "✕ offline"
        else -> "○ offline"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RuntimeBanner(text: String, isError: Boolean = false) {
    Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
fun MessageBubble(message: UiMessage) {
    if (message.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    message.parts.forEach { part ->
                        when (part.str("type")) {
                            "text" -> part.str("text")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            "file" -> Text("[file] ${part.str("filename")}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            message.parts.forEach { part ->
                when (part.str("type")) {
                    "text" -> {
                        val text = part.str("text").orEmpty()
                        // Synthetic text parts may carry diff metadata for edits.
                        val diff = (part["diff"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        MarkdownText(text)
                        if (!diff.isNullOrBlank() &&
                            (diff.startsWith("@@") || diff.contains("\n@@") || diff.startsWith("---"))
                        ) {
                            DiffCard(diff)
                        }
                    }
                    "reasoning", "thinking" -> ReasoningCard(part)
                    "tool", "tool-invocation" -> ToolCallCard(part)
                    "step-start" -> {}
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    request: dev.opencode.android.opencode.Api.PermissionRequest,
    onReply: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Permission requested", style = MaterialTheme.typography.labelLarge)
            Text(
                describePermission(request.permission),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row {
                TextButton(onClick = { onReply("once") }) { Text("Allow once") }
                TextButton(onClick = { onReply("always") }) { Text("Always allow") }
                TextButton(onClick = { onReply("reject") }) { Text("Deny", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun describePermission(p: String): String = when (p) {
    "bash" -> "The agent wants to run a shell command in your project."
    "edit" -> "The agent wants to modify files in your project."
    "webfetch" -> "The agent wants to fetch a web resource."
    "external_directory" -> "The agent wants to access a directory outside this project."
    else -> "The agent requests approval for: $p"
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = { Text("Message OpenCode…") },
                modifier = Modifier.weight(1f),
                maxLines = 6,
            )
            if (busy) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop generation", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<Pair<String, String>>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conversations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onNew) { Text("New") }
        }
        if (sessions.isEmpty()) {
            Text("No conversations yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sessions.forEach { (id, title) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onSelect(id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        title,
                        fontWeight = if (id == currentId) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                TextButton(onClick = { onDelete(id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
