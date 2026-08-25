package com.opencode.client.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.client.core.util.Time
import com.opencode.client.domain.ToolPartUi
import com.opencode.client.domain.ToolStateKind
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

/**
 * Agent activity cards: compact by default, expandable to full input/output transparency.
 *
 * Known tools get tailored presentation (bash, read, edit, search, fetch, task, todo); anything
 * unknown falls through to the generic renderer so future server tools are still fully visible.
 */
@Composable
fun ToolCard(tool: ToolPartUi, modifier: Modifier = Modifier) {
    when (tool.toolName.lowercase()) {
        "bash", "shell" -> BashToolCard(tool, modifier)
        "read" -> FileToolCard(tool, modifier, verb = "Read")
        "write" -> FileToolCard(tool, modifier, verb = "Write")
        "edit", "multiedit", "patch" -> EditToolCard(tool, modifier)
        "glob", "grep", "search", "find" -> SearchToolCard(tool, modifier)
        "webfetch", "fetch", "websearch" -> WebToolCard(tool, modifier)
        "task", "agent" -> TaskToolCard(tool, modifier)
        "todowrite", "todo" -> TodoToolCard(tool, modifier)
        else -> GenericToolCard(tool, modifier)
    }
}

@Composable
private fun ToolShell(
    tool: ToolPartUi,
    headline: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val ext = AppTheme.extended
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolStatusDot(tool.state)
                Spacer(Modifier.width(10.dp))
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ext.textFaint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        headline.ifBlank { tool.toolName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    tool.durationMs?.let {
                        Text(
                            Time.duration(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textFaint
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand details",
                    tint = ext.textFaint,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(color = ext.border.copy(alpha = 0.6f))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
                    content()

                    // Full raw transparency section.
                    RawSection(label = "Input", text = prettyJsonOrNull(tool.inputJson.toString()) ?: tool.inputJson?.toString())
                    tool.output?.takeIf { it.isNotBlank() }?.let {
                        RawSection(label = "Output", text = it)
                    }
                    tool.errorText?.takeIf { it.isNotBlank() }?.let {
                        RawSection(label = "Error", text = it, isError = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${tool.toolName} · ${tool.state.name.lowercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textFaint,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy tool output",
                            tint = ext.textFaint,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    clipboard.setText(
                                        AnnotatedString(
                                            (prettyJsonOrNull(tool.inputJson.toString()) ?: "") +
                                                "\n\n" + (tool.output ?: "") + (tool.errorText ?: "")
                                        )
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RawSection(label: String, text: String?, isError: Boolean = false) {
    val value = text?.takeIf { it.isNotBlank() } ?: return
    val ext = AppTheme.extended
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) ext.error else ext.textFaint
        )
        Spacer(Modifier.size(4.dp))
        CodeBlock(
            code = value,
            maxVisibleLines = 24,
            title = null
        )
    }
}

@Composable
private fun ToolStatusDot(state: ToolStateKind) {
    val ext = AppTheme.extended
    val color = when (state) {
        ToolStateKind.COMPLETED -> ext.success
        ToolStateKind.FAILED -> ext.error
        ToolStateKind.RUNNING -> MaterialTheme.colorScheme.primary
        ToolStateKind.PENDING -> ext.textFaint
    }
    val alpha = if (state == ToolStateKind.RUNNING) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 1f, targetValue = 0.35f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(700),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "alpha"
        ).value
    } else 1f
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun BashToolCard(tool: ToolPartUi, modifier: Modifier) {
    val command = summarizeInput(tool.inputJson, listOf("command", "script"))
        ?: tool.title ?: "shell"
    val exit = metadataInt(tool.metadataJson, "exitCode")
    ToolShell(tool, headline = command, icon = Icons.Outlined.Terminal, modifier = modifier) {
        exit?.let {
            Text(
                if (it == 0) "Exit code 0" else "Failed with exit code $it",
                style = MaterialTheme.typography.labelMedium,
                color = if (it == 0) AppTheme.extended.success else AppTheme.extended.error
            )
        }
    }
}

@Composable
private fun FileToolCard(tool: ToolPartUi, modifier: Modifier, verb: String) {
    val path = summarizeInput(tool.inputJson, listOf("filePath", "file_path", "path", "notebookPath")) ?: tool.title.orEmpty()
    val preview = metadataString(tool.metadataJson, "preview") ?: tool.output
    ToolShell(tool, headline = "$verb $path", icon = Icons.Outlined.Description, modifier = modifier) {
        preview?.takeIf { it.isNotBlank() && it.length < 4000 }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditToolCard(tool: ToolPartUi, modifier: Modifier) {
    val path = summarizeInput(tool.inputJson, listOf("filePath", "file_path", "path", "notebookPath")) ?: ""
    val adds = metadataInt(tool.metadataJson, "additions")
    val dels = metadataInt(tool.metadataJson, "deletions")
    val diffRaw = metadataString(tool.metadataJson, "diff")

    ToolShell(
        tool = tool,
        headline = buildString {
            append("Modified ")
            append(path.substringAfterLast('/').ifBlank { "file" })
        },
        icon = Icons.Outlined.EditNote,
        modifier = modifier
    ) {
        if (!path.isBlank()) {
            Text(path, style = MaterialTheme.typography.labelSmall, fontFamily = AppMonospace, color = AppTheme.extended.textFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (adds != null) StatChip("+${adds}", AppTheme.extended.diffAddFg)
            if (dels != null) StatChip("−${dels}", AppTheme.extended.diffDelFg)
            if (adds == null && dels == null) {
                Text(
                    "File changed on disk",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.extended.textFaint
                )
            }
        }
        if (diffRaw != null) {
            CodeBlock(code = diffRaw, title = "diff", maxVisibleLines = 20)
        }
    }
}

@Composable
private fun SearchToolCard(tool: ToolPartUi, modifier: Modifier) {
    val pattern = summarizeInput(tool.inputJson, listOf("pattern", "query", "glob"))
        ?: tool.title ?: "search"
    ToolShell(tool, headline = pattern, icon = Icons.Outlined.Search, modifier = modifier) {
        val out = tool.output
        val resultCount = out?.count { it == '\n' }?.plus(if (out.isNotBlank()) 1 else 0)
        if (resultCount != null && resultCount > 0) {
            Text("$resultCount results", style = MaterialTheme.typography.labelSmall, color = AppTheme.extended.info)
        }
    }
}

@Composable
private fun WebToolCard(tool: ToolPartUi, modifier: Modifier) {
    val target = summarizeInput(tool.inputJson, listOf("url", "query")) ?: tool.title ?: "web"
    ToolShell(tool, headline = target, icon = Icons.Outlined.Language, modifier = modifier) {}
}

@Composable
private fun TaskToolCard(tool: ToolPartUi, modifier: Modifier) {
    val desc = summarizeInput(tool.inputJson, listOf("description", "prompt", "agent")) ?: tool.title ?: "subagent"
    ToolShell(tool, headline = desc, icon = Icons.Outlined.AccountTree, modifier = modifier) {}
}

@Composable
private fun TodoToolCard(tool: ToolPartUi, modifier: Modifier) {
    // Render todos from input JSON if present.
    val items = parseTodos(tool.inputJson)
    ToolShell(tool, headline = "Updated plan (${items.size} steps)", icon = Icons.Outlined.Checklist, modifier = modifier) {
        if (items.isEmpty()) {
            GenericDetails(tool)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (t.status) {
                                "completed" -> "☑"
                                "in_progress" -> "◐"
                                else -> "○"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (t.status) {
                                "completed" -> AppTheme.extended.success
                                "in_progress" -> MaterialTheme.colorScheme.primary
                                else -> AppTheme.extended.textFaint
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(t.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private data class DemoTodo(val content: String, val status: String)

private fun parseTodos(input: kotlinx.serialization.json.JsonObject?): List<DemoTodo> {
    input ?: return emptyList()
    val arr = input["todos"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val c = (obj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
        val s = (obj["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "pending"
        DemoTodo(c, s)
    }
}

/** Universal fallback card - guarantees visibility for any current or future tool. */
@Composable
fun GenericToolCard(tool: ToolPartUi, modifier: Modifier = Modifier) {
    ToolShell(tool, headline = tool.title ?: tool.toolName, icon = Icons.Outlined.BuildCircle, modifier = modifier) {
        GenericDetails(tool)
    }
}

@Composable
private fun GenericDetails(tool: ToolPartUi) {
    if (tool.output.isNullOrBlank() && tool.errorText.isNullOrBlank() && tool.inputJson == null) {
        Text(
            "No structured details were provided for this tool.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.extended.textFaint
        )
    }
}
