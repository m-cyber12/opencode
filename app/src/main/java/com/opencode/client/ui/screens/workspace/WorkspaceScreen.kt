package com.opencode.client.ui.screens.workspace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.client.AppContainer
import com.opencode.client.domain.ConnectionState
import com.opencode.client.domain.ModelInfo
import com.opencode.client.domain.ModelRef
import com.opencode.client.domain.Role
import com.opencode.client.engine.ChatEngine
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.AgentPickerSheet
import com.opencode.client.ui.components.CommandPaletteSheet
import com.opencode.client.ui.components.ComposerBar
import com.opencode.client.ui.components.ConnectionBadge
import com.opencode.client.ui.components.EmptyState
import com.opencode.client.ui.components.ErrorBanner
import com.opencode.client.ui.components.MessageItem
import com.opencode.client.ui.components.ModelPickerSheet
import com.opencode.client.ui.components.OfflineNotice
import com.opencode.client.ui.components.PermissionCard
import com.opencode.client.ui.components.SessionDrawerContent
import com.opencode.client.ui.components.TodoPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The agent workspace: chat stream + session drawer + live tool activity + permission gates.
 * All conversational state flows from [ChatEngine]; nothing here polls.
 */
class ChatViewModel(
    val sessionId: String,
    private val container: AppContainer,
) : ViewModel() {

    data class Ui(
        val engineState: ChatEngine.State = ChatEngine.State(""),
        val providers: List<com.opencode.client.domain.ProviderInfo> = emptyList(),
        val agents: List<com.opencode.client.domain.AgentInfo> = emptyList(),
        val commands: List<com.opencode.client.domain.CommandInfo> = emptyList(),
        val selectedModel: ModelInfo? = null,
        val selectedAgent: String? = null,
        val showReasoning: Boolean = true
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    val engine: ChatEngine = ChatEngine(
        sessionId = sessionId,
        sessions = container.sessionRepo,
        externalEvents = container.serverController.events,
        resyncTick = container.serverController.resyncTick,
        scope = viewModelScope
    )

    init {
        viewModelScope.launch {
            engine.state.collect { s -> _ui.value = _ui.value.copy(engineState = s) }
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(showReasoning = container.settings.value.showReasoning)
            engine.load()
            loadContext()
        }
    }

    private fun loadContext() {
        viewModelScope.launch {
            when (val p = container.configRepo.providers()) {
                is com.opencode.client.core.Outcome.Ok -> {
                    if (_ui.value.selectedModel == null && p.value.isNotEmpty()) pickDefaultModel(p.value)
                    _ui.value = _ui.value.copy(providers = p.value)
                }
                else -> Unit // capability absent; model chip shows placeholder
            }
        }
        viewModelScope.launch {
            when (val a = container.configRepo.agents()) {
                is com.opencode.client.core.Outcome.Ok ->
                    _ui.value = _ui.value.copy(agents = a.value.filter { it.isPrimary })
                else -> Unit
            }
        }
        viewModelScope.launch {
            when (val c = container.configRepo.commands()) {
                is com.opencode.client.core.Outcome.Ok -> _ui.value = _ui.value.copy(commands = c.value)
                else -> Unit
            }
        }
    }

    private fun pickDefaultModel(providers: List<com.opencode.client.domain.ProviderInfo>) {
        val preferredOrder = listOf("anthropic", "openai", "google", "github-copilot", "ollama")
        val provider = providers.firstOrNull { it.id in preferredOrder } ?: providers.firstOrNull()
        val model = provider?.models?.firstOrNull()
        if (model != null) _ui.value = _ui.value.copy(selectedModel = model)
    }

    fun selectModel(model: ModelInfo) {
        _ui.value = _ui.value.copy(selectedModel = model)
    }

    fun selectAgent(agent: String?) {
        _ui.value = _ui.value.copy(selectedAgent = agent)
    }

    fun send(text: String) {
        viewModelScope.launch {
            engine.send(
                text = text,
                attachments = emptyList(),
                model = _ui.value.selectedModel?.let { ModelRef(it.providerId, it.modelId) },
                agent = _ui.value.selectedAgent
            )
        }
    }

    /** Re-sends the most recent user prompt as a fresh turn (assistant error recovery). */
    fun retryLast() {
        viewModelScope.launch {
            val lastUserText = engine.state.value.messages
                .lastOrNull { it.role == Role.USER }
                ?.parts?.filterIsInstance<com.opencode.client.domain.TextPartUi>()
                ?.joinToString("\n") { it.text }
                ?: return@launch
            send(lastUserText)
        }
    }

    fun runCommand(command: com.opencode.client.domain.CommandInfo, arguments: String) {
        viewModelScope.launch {
            when (val res = container.sessionRepo.runCommand(sessionId, command, arguments)) {
                is com.opencode.client.core.Outcome.Ok -> Unit // result arrives via events
                is com.opencode.client.core.Outcome.Err -> engine.reportError(res.error)
            }
        }
    }

    fun abort() {
        viewModelScope.launch { engine.abort() }
    }

    fun respondPermission(response: String) {
        val request = engine.state.value.pendingPermission ?: return
        viewModelScope.launch { engine.respondToPermission(request, response) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { container.sessionRepo.deleteSession(id) }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch { container.sessionRepo.renameSession(id, title) }
    }

    fun dismissError() = engine.reportError(null)

    override fun onCleared() {
        engine.dispose()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    sessionId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenDiff: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ChatViewModel = viewModel(
        key = "chat-$sessionId",
        factory = simpleFactory { ChatViewModel(sessionId, container) }
    )
    val ui by vm.ui.collectAsState()
    val engineState = ui.engineState
    val connection by container.serverController.connectionState.collectAsState()

    val drawerState = rememberDrawerStateSafely()
    val scope = rememberCoroutineScope()

    var composerText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showAgentSheet by remember { mutableStateOf(false) }
    var showCommandSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            container.settings.value.notificationsEnabled
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val listState = rememberLazyListState()
    val messages = engineState.messages

    val nearBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.parts?.size, messages.lastOrNull()?.isStreaming) {
        if (nearBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SessionDrawerContent(
                    sessionRepo = container.sessionRepo,
                    events = container.serverController.events,
                    busySessions = container.serverController.busySessions.collectAsState().value,
                    activeSessionId = sessionId,
                    onNewChat = {
                        scope.launch { drawerState.close() }
                        onNewSession()
                    },
                    onPickSession = { id ->
                        scope.launch { drawerState.close() }
                        onSessionSelected(id)
                    },
                    onRename = { id -> renameTarget = id },
                    onDelete = { id ->
                        vm.deleteSession(id)
                        if (id == sessionId) onNewSession()
                    },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Sessions")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                currentTitle(engineState),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            ConnectionBadge(state = connection)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenFiles) {
                            Icon(Icons.Outlined.Description, contentDescription = "Project files")
                        }
                        IconButton(onClick = onOpenDiff) {
                            Icon(Icons.Outlined.Difference, contentDescription = "Changes")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Activity log") },
                                onClick = { menuOpen = false; onOpenActivity() }
                            )
                            DropdownMenuItem(
                                text = { Text("Abort agent") },
                                enabled = engineState.busy,
                                onClick = { menuOpen = false; vm.abort() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuOpen = false; onOpenSettings() }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    engineState.pendingPermission?.let { req ->
                        PermissionCard(
                            request = req,
                            onRespond = vm::respondPermission,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    TodoPanel(todos = engineState.todos, modifier = Modifier.padding(horizontal = 12.dp))
                    OfflineNotice(connection = connection, onRetry = { /* automatic */ })
                    engineState.error?.let { err ->
                        ErrorBanner(
                            message = err.userMessage,
                            technical = err.technical,
                            onDismiss = vm::dismissError,
                            actionLabel = "Retry",
                            onAction = { vm.dismissError(); vm.retryLast() },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    ComposerBar(
                        text = composerText,
                        onTextChange = { v ->
                            composerText = v
                            if (v.startsWith("/") && !v.contains(' ')) showCommandSheet = true
                        },
                        busy = engineState.busy,
                        enabled = connection is ConnectionState.Connected || connection is ConnectionState.Reconnecting,
                        agentLabel = ui.selectedAgent ?: "build",
                        modelLabel = ui.selectedModel?.displayName ?: "default",
                        onSend = {
                            val text = composerText.trim()
                            if (text.isNotEmpty()) {
                                vm.send(text)
                                composerText = ""
                            }
                        },
                        onStop = vm::abort,
                        onAgentClick = { showAgentSheet = true },
                        onModelClick = { showModelSheet = true },
                        onAttachClick = null
                    )
                }
            }
        ) { padding ->
            when {
                engineState.loading && messages.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                messages.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "What are we building?",
                            hint = "Describe a task - OpenCode reads your codebase, edits files and runs commands on your machine."
                        )
                    }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            showReasoning = ui.showReasoning,
                            onRetryLast = if (message.errorMessage != null) ({ vm.retryLast() }) else null
                        )
                    }
                }
            }
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            providers = ui.providers,
            selected = ui.selectedModel,
            onSelect = { vm.selectModel(it); showModelSheet = false },
            onDismiss = { showModelSheet = false }
        )
    }
    if (showAgentSheet) {
        AgentPickerSheet(
            agents = ui.agents,
            selected = ui.selectedAgent,
            onSelect = { vm.selectAgent(it); showAgentSheet = false },
            onDismiss = { showAgentSheet = false }
        )
    }
    if (showCommandSheet) {
        CommandPaletteSheet(
            commands = ui.commands,
            filter = composerText,
            onPick = { cmd ->
                showCommandSheet = false
                val args = composerText.substringAfter(' ').trim()
                vm.runCommand(cmd, args)
                composerText = ""
            },
            onDismiss = { showCommandSheet = false }
        )
    }
    renameTarget?.let { targetId ->
        RenameDialog(
            onConfirm = { newTitle ->
                vm.renameSession(targetId, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

private fun currentTitle(state: ChatEngine.State): String {
    val firstUser = state.messages.firstOrNull { it.role == Role.USER }
    val text = firstUser?.parts
        ?.filterIsInstance<com.opencode.client.domain.TextPartUi>()
        ?.firstOrNull()?.text.orEmpty()
    return text.lineSequence().firstOrNull()?.take(48)?.ifBlank { null } ?: "New chat"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDrawerStateSafely() =
    androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)

@Composable
private fun RenameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
