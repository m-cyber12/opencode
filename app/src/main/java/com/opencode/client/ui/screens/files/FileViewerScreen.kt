package com.opencode.client.ui.screens.files

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.opencode.client.domain.FileContentInfo
import com.opencode.client.ui.common.simpleFactory
import com.opencode.client.ui.components.CodeBlock
import com.opencode.client.ui.components.EmptyState
import com.opencode.client.core.text.SyntaxHighlighter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FileViewerViewModel(
    private val path: String,
    private val container: AppContainer,
) : ViewModel() {

    data class Ui(
        val loading: Boolean = true,
        val content: FileContentInfo? = null,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    init {
        viewModelScope.launch {
            when (val res = container.fileRepo.readFile(path)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(loading = false, content = res.value)
                is Outcome.Err -> _ui.value = _ui.value.copy(loading = false, error = res.error.userMessage)
            }
        }
    }
}

/** Full-screen file viewer with line numbers and syntax-aware coloring. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    path: String,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: FileViewerViewModel = viewModel(
        key = "viewer-$path",
        factory = simpleFactory { FileViewerViewModel(path, container) }
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
                    Text(path.substringAfterLast('/'), maxLines = 1)
                }
            )
        },
        bottomBar = {
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { androidx.compose.material3.CircularProgressIndicator() }

                ui.error != null -> EmptyState(title = "Could not read file", hint = ui.error)

                ui.content?.binary == true -> EmptyState(
                    title = "Binary file",
                    hint = "Preview is not available for ${ui.content?.mimeType ?: "this type"}."
                )

                else -> {
                    val content = ui.content
                    CodeBlock(
                        code = content?.content.orEmpty(),
                        language = SyntaxHighlighter.languageForFile(path),
                        title = null,
                        showLineNumbers = true,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                }
            }
        }
    }
}
