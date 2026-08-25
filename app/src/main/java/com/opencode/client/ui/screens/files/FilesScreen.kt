package com.opencode.client.ui.screens.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.opencode.client.domain.FileNodeInfo
import com.opencode.client.domain.TextSearchMatch
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FilesViewModel(private val container: AppContainer) : ViewModel() {

    data class Ui(
        val path: String = ".",
        val entries: List<FileNodeInfo> = emptyList(),
        val loading: Boolean = false,
        val searching: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<TextSearchMatch> = emptyList(),
        val fuzzyResults: List<String> = emptyList(),
        val error: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    init {
        navigate(".")
    }

    fun navigate(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, path = path)
            when (val res = container.fileRepo.list(path)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(entries = res.value, loading = false, searchQuery = "", fuzzyResults = emptyList(), searchResults = emptyList())
                is Outcome.Err -> _ui.value = _ui.value.copy(loading = false, error = res.error.userMessage)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _ui.value = _ui.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _ui.value = _ui.value.copy(fuzzyResults = emptyList(), searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            // Fuzzy filename find first; falls back visually to content matches when present.
            when (val files = container.fileRepo.searchFiles(query)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(fuzzyResults = files.value)
                else -> Unit
            }
            if (query.length >= 2) {
                when (val text = container.fileRepo.searchText(query)) {
                    is Outcome.Ok -> _ui.value = _ui.value.copy(searchResults = text.value.take(60))
                    else -> Unit
                }
            }
        }
    }

    fun parentPath(): String {
        val current = _ui.value.path.trimEnd('/')
        return current.substringBeforeLast('/', missingDelimiterValue = ".").ifBlank { "." }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: FilesViewModel = viewModel(factory = simpleFactory { FilesViewModel(container) })
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Files · ${projectLeaf(ui.path)}") },
                actions = {
                    IconButton(onClick = { vm.navigate(".") }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Root")
                    }
                }
            )
        },
        bottomBar = {
            if (ui.path != ".") {
                Row(Modifier.padding(12.dp)) {}
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            OutlinedTextField(
                value = ui.searchQuery,
                onValueChange = vm::onSearchQueryChange,
                placeholder = { Text("Find files or search contents…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            when {
                ui.error != null -> EmptyState(title = "Could not list this directory", hint = ui.error)

                ui.searchQuery.isNotBlank() -> SearchResults(ui, vm, onOpenFile)

                ui.loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { androidx.compose.material3.CircularProgressIndicator() }

                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = ui.path != ".") { vm.navigate(vm.parentPath()) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↑  ..", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    items(ui.entries, key = { it.path }) { node ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    node.name,
                                    color = if (node.ignored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (node.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                                    contentDescription = if (node.isDirectory) "Folder" else "File",
                                    tint = if (node.isDirectory) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                if (!node.isDirectory) {
                                    Spacer(Modifier.size(0.dp))
                                }
                            },
                            modifier = Modifier.clickable {
                                if (node.isDirectory) vm.navigate(node.path) else onOpenFile(node.path)
                            }
                        )
                    }
                    if (ui.entries.isEmpty()) {
                        item {
                            EmptyState(title = "Empty directory", hint = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(ui: FilesViewModel.Ui, vm: FilesViewModel, onOpenFile: (String) -> Unit) {
    LazyColumn {
        if (ui.fuzzyResults.isNotEmpty()) {
            item { SectionLabel("Matching paths") }
            items(ui.fuzzyResults, key = { "f-$it" }) { path ->
                ListItem(
                    headlineContent = { Text(path, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val dir = path.substringBeforeLast('/', missingDelimiterValue = ".")
                        if (path.substringAfterLast('/').contains('.')) onOpenFile(path) else vm.navigate(path)
                        Unit
                    }
                )
            }
        }
        if (ui.searchResults.isNotEmpty()) {
            item { SectionLabel("Text matches") }
            items(ui.searchResults, key = { "t-${it.path}-${it.lineNumber}" }) { match ->
                ListItem(
                    headlineContent = { Text(match.lineText, maxLines = 2, style = MaterialTheme.typography.bodySmall) },
                    supportingContent = { Text("${match.path}:${match.lineNumber}", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.clickable { onOpenFile(match.path) }
                )
            }
        }
        if (ui.fuzzyResults.isEmpty() && ui.searchResults.isEmpty()) {
            item { EmptyState(title = "No matches", hint = "Try a different query.") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun projectLeaf(path: String): String = path.substringAfterLast('/').ifBlank { "root" }
