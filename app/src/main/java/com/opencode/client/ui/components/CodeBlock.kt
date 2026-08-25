package com.opencode.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.opencode.client.core.text.SyntaxHighlighter
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

/**
 * Syntax-highlighted code block with a language chip, horizontal scrolling, optional line-number
 * gutter, copy button and size-capped rendering with "Show all" for very large payloads.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    title: String? = null,
    showLineNumbers: Boolean = false,
    maxVisibleLines: Int = 400,
    modifier: Modifier = Modifier,
) {
    val ext = AppTheme.extended
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(code) { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }

    val rawLines = code.trimEnd('\n').split('\n')
    val truncated = !expanded && rawLines.size > maxVisibleLines
    val visibleCode = if (truncated) {
        rawLines.take(maxVisibleLines).joinToString("\n")
    } else code

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (title ?: language ?: "code").lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textFaint,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) {
                    Icon(
                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        tint = if (copied) ext.success else ext.textFaint
                    )
                }
            }
            val scroll = rememberScrollState()
            // Resolve theme colors outside the remember{} builder (composable context rule).
            val cKeyword = AppTheme.extended.codeKeyword
            val cString = AppTheme.extended.codeString
            val cComment = AppTheme.extended.codeComment
            val cNumber = AppTheme.extended.codeNumber
            val cAnnotation = AppTheme.extended.codeAnnotation
            val cFunction = AppTheme.extended.codeFunction
            val cBase = AppTheme.extended.codeBase
            val cFaint = AppTheme.extended.textFaint

            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
            ) {
                val annotated: AnnotatedString = remember(visibleCode, language, showLineNumbers) {
                    buildAnnotatedString {
                        fun colorFor(kind: SyntaxHighlighter.TokenKind) = when (kind) {
                            SyntaxHighlighter.TokenKind.KEYWORD -> cKeyword
                            SyntaxHighlighter.TokenKind.STRING -> cString
                            SyntaxHighlighter.TokenKind.COMMENT -> cComment
                            SyntaxHighlighter.TokenKind.NUMBER -> cNumber
                            SyntaxHighlighter.TokenKind.ANNOTATION -> cAnnotation
                            SyntaxHighlighter.TokenKind.FUNCTION -> cFunction
                            SyntaxHighlighter.TokenKind.BASE -> cBase
                        }
                        val lines = visibleCode.split('\n')
                        lines.forEachIndexed { index, line ->
                            if (showLineNumbers) {
                                pushStyle(SpanStyle(color = cFaint))
                                append((index + 1).toString().padStart(3))
                                append("  ")
                                pop()
                            }
                            val spans = SyntaxHighlighter.tokenize(line, language)
                            for (span in spans) {
                                pushStyle(SpanStyle(color = colorFor(span.kind)))
                                append(line.substring(span.start, span.end))
                                pop()
                            }
                            if (index != lines.lastIndex) append('\n')
                        }
                    }
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMonospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (truncated) {
                Text(
                    text = "Show ${rawLines.size - maxVisibleLines} more lines",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
