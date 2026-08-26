package dev.opencode.android.ui.components

import Api.str
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Native rendering of an OpenCode tool invocation part:
 *   { "type":"tool", "tool":"bash", "state":{"status":"completed","input":{…},"output":"…"} }
 * Tool activity renders inline within the conversation (spec §23).
 */
@Composable
fun ToolCallCard(part: JsonObject, modifier: Modifier = Modifier) {
    val toolName = part.str("tool") ?: "tool"
    val state = part["state"] as? JsonObject ?: JsonObject(emptyMap())
    val status = state.str("status") ?: "pending"
    val input = state["input"] as? JsonObject ?: JsonObject(emptyMap())
    val output = state.str("output")?.takeIf { it.isNotBlank() }
    val title = inputTitle(toolName, input)

    var expanded by rememberSaveable(part.toString()) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        toolName == "bash" -> Icons.Filled.Terminal
                        status == "error" -> Icons.Filled.Error
                        status == "completed" -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = when (status) {
                        "error" -> MaterialTheme.colorScheme.error
                        "completed" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text("Tool · $toolName", style = MaterialTheme.typography.labelMedium)
                    title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                        )
                    }
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "collapse" else "expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                output?.let {
                    Text(
                        truncate(it, 8000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                    )
                }
                if (input.isNotEmpty()) {
                    Text(
                        input.entries.joinToString("\n") { "${it.key}: ${primitiveToString(it.value)}" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReasoningCard(part: JsonObject, modifier: Modifier = Modifier) {
    val text = part.str("text")?.takeIf { it.isNotBlank() } ?: return
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).clickable { expanded = !expanded }) {
            Text(
                if (expanded) "Thinking…" else "Thinking… (tap to expand)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun inputTitle(toolName: String, input: JsonObject): String? = when (toolName) {
    "bash" -> input.str("command")?.let { "$ ${truncate(it, 200)}" }
    "read" -> input.str("filePath")?.let { "read $it" }
    "edit" -> listOfNotNull(input.str("filePath")).joinToString(" ") { it }?.ifEmpty { null }?.let { "edit $it" }
    "write" -> input.str("filePath")?.let { "write $it" }
    "glob", "grep", "list" -> input.str("pattern")?.let { "$toolName ${truncate(it, 120)}" }
    "task" -> input.str("description")?.let { truncate(it, 120) }
    else -> input.entries.firstOrNull()?.let { "${it.key}: ${truncate(primitiveToString(it.value), 160)}" }
}

private fun primitiveToString(v: kotlinx.serialization.json.JsonElement): String =
    (v as? JsonPrimitive)?.contentOrNull ?: v.toString()

private fun truncate(s: String, n: Int): String = if (s.length <= n) s else s.take(n) + "…"
