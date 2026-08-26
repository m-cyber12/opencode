package dev.opencode.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Renders unified diffs produced by file edits (session diff / tool output)
 * with add/remove line coloring.
 */
object DiffParser {
    data class Line(val kind: Kind, val text: String) {
        enum class Kind { ADD, DEL, HUNK, CONTEXT }
    }

    fun parse(diff: String): List<Line> = diff.lines().mapNotNull { l ->
        when {
            l.startsWith("+++") || l.startsWith("---") -> null
            l.startsWith("@@") -> Line(Line.Kind.HUNK, l)
            l.startsWith("+") -> Line(Line.Kind.ADD, l)
            l.startsWith("-") -> Line(Line.Kind.DEL, l)
            else -> Line(Line.Kind.CONTEXT, l)
        }
    }
}

@Composable
fun DiffCard(diff: String, modifier: Modifier = Modifier) {
    if (diff.isBlank()) return
    val lines = remember(diff) { DiffParser.parse(diff) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            lines.take(400).forEach { line ->
                val bg = when (line.kind) {
                    DiffParser.Line.Kind.ADD -> Color(0xFF2EA043).copy(alpha = 0.16f)
                    DiffParser.Line.Kind.DEL -> Color(0xFFF85149).copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
                Text(
                    line.text.ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when (line.kind) {
                        DiffParser.Line.Kind.ADD -> Color(0xFF7EE787)
                        DiffParser.Line.Kind.DEL -> Color(0xFFFFA198)
                        DiffParser.Line.Kind.HUNK -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
            }
            if (lines.size > 400) {
                Text("… ${lines.size - 400} more lines", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
