package com.opencode.client.ui.screens.diff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.client.AppContainer
import com.opencode.client.core.Outcome
import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.engine.ServerController
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.DiffFileCard
import com.opencode.client.ui.components.EmptyState
import com.opencode.client.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiffViewModel(
    private val sessionId: String,
    private val container: AppContainer,
) : ViewModel() {

    data class Ui(
        val loading: Boolean = true,
        val files: List<DiffFileInfo> = emptyList(),
        val error: String? = null,
        val branch: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    init {
        load()
        // Live updates while the sheet is open.
        viewModelScope.launch {
            container.serverController.events.collect { event ->
                if (event is com.opencode.client.opencode.event.OpenCodeEvent.FileEdited ||
                    event is com.opencode.client.opencode.event.OpenCodeEvent.SessionDiff
                ) {
                    load()
                }
            }
        }
        viewModelScope.launch {
            when (val vcs = container.projectRepo.vcsBranch()) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(branch = vcs.value)
                else -> Unit
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            when (val res = container.sessionRepo.sessionDiff(sessionId)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(loading = false, files = res.value, error = null)
                is Outcome.Err -> _ui.value = _ui.value.copy(
                    loading = false,
                    error = res.error.userMessage
                )
            }
        }
    }
}

/** Session changes: per-file unified diffs, expandable, live-refreshing from server events. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    sessionId: String,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: DiffViewModel = viewModel(
        key = "diff-$sessionId",
        factory = simpleFactory { DiffViewModel(sessionId, container) }
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Changes", style = MaterialTheme.typography.titleMedium)
                        ui.branch?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = AppTheme.extended.info)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.loading && ui.files.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }

                ui.error != null && ui.files.isEmpty() -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    com.opencode.client.ui.components.ErrorBanner(
                        message = ui.error ?: "",
                        actionLabel = "Retry",
                        onAction = vm::load
                    )
                }

                ui.files.isEmpty() -> EmptyState(
                    title = "No file changes yet",
                    hint = "Edits made by the agent in this session appear here."
                )

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val adds = ui.files.sumOf { it.additions }
                            val dels = ui.files.sumOf { it.deletions }
                            com.opencode.client.ui.components.StatChip("+$adds", AppTheme.extended.diffAddFg)
                            com.opencode.client.ui.components.StatChip("−$dels", AppTheme.extended.diffDelFg)
                            com.opencode.client.ui.components.StatChip("${ui.files.size} files", AppTheme.extended.textFaint)
                        }
                    }
                    items(ui.files, key = { it.file }) { file ->
                        DiffFileCard(file = file)
                    }
                }
            }
        }
    }
}
