package com.opencode.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.domain.DiffHunk
import com.opencode.client.domain.LineKind
import com.opencode.client.engine.DiffEngine
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

/**
 * Mobile-optimized unified diff renderer: per-file stats header, hunk headers, colored add/del
 * lines with line numbers and horizontal scrolling for long lines.
 */
@Composable
fun DiffFileCard(
    file: DiffFileInfo,
    expandedByDefault: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rendered = remember(file) { DiffEngine.render(file) }
    var expanded by rememberSaveable(file.file) { mutableStateOf(expandedByDefault) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppTheme.extended.border)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    file.file.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                StatChip("+${file.additions}", AppTheme.extended.diffAddFg)
                Spacer(Modifier.width(6.dp))
                StatChip("−${file.deletions}", AppTheme.extended.diffDelFg)
            }
            if (expanded) {
                if (rendered.hunks.isEmpty()) {
                    Text(
                        "No textual changes (mode/binary change).",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.extended.textFaint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    rendered.hunks.forEach { hunk -> HunkBlock(hunk) }
                }
            }
        }
    }
}

@Composable
private fun HunkBlock(hunk: DiffHunk) {
    HorizontalDivider(color = AppTheme.extended.border.copy(alpha = 0.6f))
    Text(
        "@@ -${hunk.oldStart} +${hunk.newStart} @@",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = AppMonospace,
        color = AppTheme.extended.info,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Column {
            hunk.lines.forEach { line ->
                val bg = when (line.kind) {
                    LineKind.ADD -> AppTheme.extended.diffAddBg
                    LineKind.DEL -> AppTheme.extended.diffDelBg
                    else -> Color.Transparent
                }
                val fg = when (line.kind) {
                    LineKind.ADD -> AppTheme.extended.diffAddFg
                    LineKind.DEL -> AppTheme.extended.diffDelFg
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.background(bg)) {
                    Text(
                        text = (line.oldNo?.toString() ?: "").padStart(4),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                        color = AppTheme.extended.textFaint,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        text = (line.newNo?.toString() ?: "").padStart(4),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                        color = AppTheme.extended.textFaint,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = when (line.kind) {
                            LineKind.ADD -> " + "
                            LineKind.DEL -> " - "
                            else -> "   "
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                        color = fg
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMonospace),
                        color = fg,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatChip(text: String, tint: Color) {
    Box(
        Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontFamily = AppMonospace)
    }
}
