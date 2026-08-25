package com.opencode.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.opencode.client.core.text.SyntaxHighlighter
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

/*
 * Native Compose markdown renderer covering the subset OpenCode responses use:
 * headings, paragraphs, bold/italic/strike/inline-code, links, fenced code blocks,
 * ordered/unordered/task lists, blockquotes, tables and horizontal rules.
 *
 * Designed for streaming: the string is re-parsed inside remember() on change - cheap for
 * typical message sizes, stateless, and allocation-friendly.
 */

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class CodeFence(val lang: String?, val code: String, val closed: Boolean) : Block
    data class ListItem(val marker: String, val text: String, val taskDone: Boolean?) : Block
    data class Quote(val lines: List<String>) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    object Rule : Block
}

private fun parseBlocks(md: String): List<Block> {
    val blocks = ArrayList<Block>()
    val lines = md.lines()
    var i = 0
    val n = lines.size

    fun isTableSeparator(s: String): Boolean =
        s.replace(" ", "").matches(Regex("\\|?(:?-+:?\\|)+(:?-+:?\\|?)"))

    while (i < n) {
        val line = lines[i]
        when {
            line.isBlank() -> i++

            line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~") -> {
                val fence = line.trimStart().take(3)
                val lang = line.trim().removePrefix(fence).trim().takeIf { it.isNotBlank() }
                val body = StringBuilder()
                i++
                var closed = false
                while (i < n) {
                    if (lines[i].trimStart().startsWith(fence)) { closed = true; i++; break }
                    body.append(lines[i]).append('\n'); i++
                }
                blocks.add(Block.CodeFence(lang, body.toString().trimEnd('\n'), closed))
            }

            Regex("^#{1,6}\\s").containsMatchIn(line) -> {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks.add(Block.Heading(level, line.dropWhile { it == '#' }.trim()))
                i++
            }

            Regex("^\\s{0,3}([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$").matches(line) -> {
                blocks.add(Block.Rule); i++
            }

            line.trimStart().startsWith(">") -> {
                val quote = ArrayList<String>()
                while (i < n && lines[i].trimStart().startsWith(">")) {
                    quote.add(lines[i].trimStart().removePrefix(">").trim()); i++
                }
                blocks.add(Block.Quote(quote))
            }

            line.trimStart().startsWith("|") && i + 1 < n && isTableSeparator(lines[i + 1]) -> {
                fun cells(row: String): List<String> =
                    row.trim().removeSuffix("|").removePrefix("|").split('|').map { it.trim() }
                val header = cells(lines[i])
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < n && lines[i].isNotBlank() && lines[i].contains('|')) {
                    rows.add(cells(lines[i])); i++
                }
                blocks.add(Block.Table(header, rows))
            }

            Regex("^\\s*([-*+]|\\d{1,3}[.)])\\s+").containsMatchIn(line) -> {
                while (i < n) {
                    val m = Regex("^(\\s*)([-*+]|\\d{1,3}[.)])\\s+(.*)$").find(lines[i]) ?: break
                    val marker = m.groupValues[2]
                    var text = m.groupValues[3]
                    i++
                    while (i < n && lines[i].isNotBlank() &&
                        !Regex("^\\s*([-*+]|\\d{1,3}[.)])\\s+").containsMatchIn(lines[i])
                    ) {
                        text += " " + lines[i].trim(); i++
                    }
                    var taskDone: Boolean? = null
                    Regex("^\\[( |x|X)\\]\\s*(.*)$").find(text)?.let { task ->
                        taskDone = task.groupValues[1].equals("x", ignoreCase = true)
                        text = task.groupValues[2]
                    }
                    blocks.add(Block.ListItem(marker, text, taskDone))
                }
            }

            else -> {
                val para = StringBuilder(line.trim())
                i++
                while (i < n && lines[i].isNotBlank() &&
                    !Regex("^\\s*(#{1,6}\\s|```|~~~|>|[-*+]\\s|\\d{1,3}[.)]\\s)").containsMatchIn(lines[i]) &&
                    !lines[i].trimStart().startsWith("|")
                ) {
                    para.append(' ').append(lines[i].trim())
                    i++
                }
                blocks.add(Block.Paragraph(para.toString()))
            }
        }
    }
    return blocks
}

/** Renders markdown into a Column of native composables. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph ->
                    Text(
                        styledInline(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )

                is Block.Heading -> Text(
                    styledInline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )

                is Block.CodeFence -> CodeBlock(
                    code = block.code,
                    language = block.lang?.let { SyntaxHighlighter.languageForFenceTag(it) },
                    title = block.lang,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                is Block.ListItem -> Row(Modifier.padding(vertical = 2.dp)) {
                    val bullet = when {
                        block.taskDone != null -> if (block.taskDone) "☑" else "☐"
                        block.marker.first().isDigit() -> "${block.marker.trimEnd('.', ')')}."
                        else -> "•"
                    }
                    Text(
                        bullet,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.taskDone == true) AppTheme.extended.success
                        else AppTheme.extended.textFaint,
                        modifier = Modifier.width(if (block.marker.first().isDigit()) 28.dp else 22.dp)
                    )
                    Text(
                        styledInline(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.taskDone == true) AppTheme.extended.textFaint
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                is Block.Quote -> Row(
                    Modifier
                        .padding(vertical = 4.dp)
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(AppTheme.extended.border, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        block.lines.forEach {
                            Text(
                                styledInline(it),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is Block.Table -> TableBlock(block)

                Block.Rule -> HorizontalDivider(
                    Modifier.padding(vertical = 8.dp),
                    color = AppTheme.extended.border
                )
            }
        }
    }
}

@Composable
private fun TableBlock(block: Block.Table) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            block.header.forEach {
                Text(
                    styledInline(it),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = AppTheme.extended.border)
        block.rows.forEach { row ->
            Row(Modifier.padding(vertical = 2.dp)) {
                row.forEachIndexed { colIdx, cell ->
                    Text(
                        styledInline(cell),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }
                repeat((block.header.size - row.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- inline spans

private val P_CODE = Regex("`([^`\\n]+)`")
private val P_BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val P_BOLD_ALT = Regex("__(.+?)__")
private val P_ITALIC = Regex("(?<![*\\w])\\*([^*\\n]+)\\*(?!\\*)")
private val P_ITALIC_ALT = Regex("(?<![\\w])_([^_\\n]+)_(?![_\\w])")
private val P_STRIKE = Regex("~~(.+?)~~")
private val P_LINK = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")
private val ALL_INLINE = listOf(P_CODE, P_BOLD, P_BOLD_ALT, P_ITALIC, P_ITALIC_ALT, P_STRIKE, P_LINK)

/** Composable entry point resolving theme colors once per text change. */
@Composable
internal fun styledInline(text: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val link = MaterialTheme.colorScheme.primary
    return remember(text, codeBg, link) { buildInline(text, codeBg, link) }
}

private fun findEarliest(text: String, from: Int, until: Int): Pair<Int, MatchResult>? {
    var best: MatchResult? = null
    var bestIdx = -1
    for ((idx, pattern) in ALL_INLINE.withIndex()) {
        val m = pattern.find(text, from, until) ?: continue
        if (best == null || m.range.first < best!!.range.first) {
            best = m
            bestIdx = idx
        }
    }
    return best?.let { bestIdx to it }
}

private fun buildInline(text: String, codeBg: Color, linkColor: Color): AnnotatedString =
    buildAnnotatedString { appendInline(text, 0, text.length, codeBg, linkColor) }

private fun AnnotatedString.Builder.appendInline(
    text: String, from: Int, until: Int, codeBg: Color, linkColor: Color
) {
    var pos = from
    while (pos < until) {
        val found = findEarliest(text, pos, until)
        if (found == null) {
            append(text.substring(pos, until))
            return
        }
        val (patternIdx, match) = found
        if (match.range.first > pos) append(text.substring(pos, match.range.first))

        when (patternIdx) {
            0 -> {
                pushStyle(SpanStyle(fontFamily = AppMonospace, background = codeBg))
                append(match.groupValues[1])
                pop()
            }
            1, 2 -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                appendInline(text, match.range.first + 2, match.range.last, codeBg, linkColor)
                pop()
            }
            3, 4 -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInline(text, match.range.first + 1, match.range.last, codeBg, linkColor)
                pop()
            }
            5 -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendInline(text, match.range.first + 2, match.range.last, codeBg, linkColor)
                pop()
            }
            else -> {
                pushLink(
                    LinkAnnotation.Url(
                        match.groupValues[2],
                        TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                    )
                )
                append(match.groupValues[1])
                pop()
            }
        }
        pos = match.range.last + 1
    }
}
