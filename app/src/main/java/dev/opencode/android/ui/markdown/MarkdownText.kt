package dev.opencode.android.ui.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dependency-free Markdown renderer covering the subset OpenCode responses use:
 * headings, bullet/numbered lists, bold, italic, inline code, fenced code blocks,
 * blockquotes, links and horizontal rules. Code blocks get lightweight,
 * regex-based syntax highlighting.
 */
object Markdown {

    private val BOLD = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__", RegexOption.DOT_MATCHES_ALL)
    private val ITALIC = Regex("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)|(?<!_)_([^_\\n]+?)_(?!_)")
    private val INLINE_CODE = Regex("`([^`\\n]+?)`")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")

    fun annotateInline(text: String, codeColor: Color, linkColor: Color): AnnotatedString =
        buildAnnotatedString {
            var rest = text
            // Tokenize by earliest match among the patterns.
            while (rest.isNotEmpty()) {
                val candidates = mutableListOf<Triple<Int, Int, AnnotatedString.() -> Unit>>()
                INLINE_CODE.find(rest)?.let { m ->
                    candidates += Triple(m.range.first, m.range.last + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeColor.copy(alpha = 0.18f))) {
                            append(m.groupValues[1])
                        }
                    }
                }
                BOLD.find(rest)?.let { m ->
                    candidates += Triple(m.range.first, m.range.last + 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
                    }
                }
                ITALIC.find(rest)?.let { m ->
                    candidates += Triple(m.range.first, m.range.last + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
                    }
                }
                LINK.find(rest)?.let { m ->
                    candidates += Triple(m.range.first, m.range.last + 1) {
                        withStyle(SpanStyle(color = linkColor)) { append(m.groupValues[1]) }
                        append(" (${m.groupValues[2]})")
                    }
                }
                val first = candidates.minByOrNull { it.first }
                if (first == null) {
                    append(rest)
                    break
                } else {
                    if (first.first > 0) append(rest.substring(0, first.first))
                    first.third()
                    rest = rest.substring(first.second)
                }
            }
        }

    data class Block(val kind: Kind, val content: String, val lang: String? = null) {
        enum class Kind { CODE, TEXT }
    }

    fun splitBlocks(md: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = md.lines()
        var i = 0
        val buf = StringBuilder()
        while (i < lines.size) {
            val line = lines[i]
            val fence = Regex("^```(\\w*)\\s*$").find(line)
            if (fence != null) {
                if (buf.isNotBlank()) blocks += Block(Block.Kind.TEXT, buf.toString().trim('\n'))
                buf.setLength(0)
                val lang = fence.groupValues[1].takeIf { it.isNotBlank() }
                i++
                val code = StringBuilder()
                while (i < lines.size && !lines[i].startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                blocks += Block(Block.Kind.CODE, code.toString(), lang)
            } else {
                buf.append(line).append('\n')
            }
            i++
        }
        if (buf.isNotBlank()) blocks += Block(Block.Kind.TEXT, buf.toString().trim('\n'))
        return blocks
    }
}

/** Minimal tokenizer-based highlighter for common languages. */
object SyntaxHighlight {
    private val KEYWORDS = mapOf(
        "kotlin" to setOf("fun","val","var","class","object","if","else","when","for","while","return","import","package","private","public","internal","suspend","data","sealed","interface","companion","this","super","null","true","false","is","as","in","try","catch","finally","throw","override","open","abstract"),
        "java" to setOf("class","interface","void","int","long","boolean","double","float","public","private","protected","static","final","new","return","if","else","for","while","try","catch","finally","throw","throws","extends","implements","import","package","null","true","false","this","super"),
        "js" to setOf("function","const","let","var","if","else","for","while","return","class","extends","new","await","async","import","from","export","default","null","undefined","true","false","typeof","instanceof","try","catch","finally","throw"),
        "ts" to setOf("function","const","let","var","if","else","for","while","return","class","extends","new","await","async","import","from","export","default","null","undefined","true","false","type","interface","enum","implements","readonly","typeof","try","catch","finally","throw"),
        "python" to setOf("def","class","if","elif","else","for","while","return","import","from","as","with","try","except","finally","raise","lambda","None","True","False","and","or","not","in","is","pass","yield","global","async","await"),
        "bash" to setOf("if","then","else","fi","for","do","done","while","case","esac","function","echo","export","cd","exit","local","return","set","unset","source","sudo","rm","cp","mv","mkdir","chmod","chown","git","npm","node","bun","curl","tar","gzip"),
        "json" to setOf("true","false","null"),
    )

    private val ALIASES = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "py" to "python", "sh" to "bash", "shell" to "bash",
        "zsh" to "bash", "console" to "bash", "javascript" to "js", "typescript" to "ts",
        "jsx" to "js", "tsx" to "ts",
    )

    fun annotate(code: String, lang: String?): AnnotatedString {
        val l = ALIASES[lang?.lowercase()] ?: lang?.lowercase()
        val keywords = KEYWORDS[l] ?: return AnnotatedString(code)
        return buildAnnotatedString {
            val tokenRe = Regex("(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|#[^\\n]*|//[^\\n]*|/\\*.*?\\*/|\\b\\d+(?:\\.\\d+)?\\b|[A-Za-z_][A-Za-z0-9_]*|.)")
            for (m in tokenRe.findAll(code)) {
                val t = m.value
                when {
                    t.startsWith("\"") || t.startsWith("'") -> withStyle(SpanStyle(color = Color(0xFF9ECE6A))) { append(t) }
                    t.startsWith("#") || t.startsWith("//") || (t.startsWith("/*") && l != "css") ->
                        withStyle(SpanStyle(color = Color(0xFF565F89), fontStyle = FontStyle.Italic)) { append(t) }
                    t.firstOrNull()?.isDigit() == true && t.all { it.isDigit() || it == '.' } ->
                        withStyle(SpanStyle(color = Color(0xFFFF9E64))) { append(t) }
                    t in keywords -> withStyle(SpanStyle(color = Color(0xFFBB9AF7), fontWeight = FontWeight.Medium)) { append(t) }
                    else -> append(t)
                }
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val inlineCodeBg = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val blocks = remember(markdown) { Markdown.splitBlocks(markdown) }

    Column(modifier) {
        blocks.forEach { block ->
            when (block.kind) {
                Markdown.Block.Kind.CODE -> {
                    Surface(
                        color = codeBg,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column {
                            block.lang?.let {
                                Text(
                                    it.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp, top = 6.dp),
                                )
                            }
                            Text(
                                remember(block.content, block.lang) { SyntaxHighlight.annotate(block.content, block.lang) },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp),
                            )
                        }
                    }
                }
                Markdown.Block.Kind.TEXT -> TextBlock(block.content, inlineCodeBg, linkColor)
            }
        }
    }
}

@Composable
private fun TextBlock(content: String, inlineCodeBg: Color, linkColor: Color) {
    val lines = content.lines()
    var listBuffer = mutableListOf<String>()
    Column {
        fun flushList() {
            if (listBuffer.isNotEmpty()) {
                listBuffer.forEach { itemText ->
                    Row(Modifier.padding(vertical = 1.dp)) {
                        Text("•  ", color = MaterialTheme.colorScheme.primary)
                        Text(
                            remember(itemText) { Markdown.annotateInline(itemText, inlineCodeBg, linkColor) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                listBuffer = mutableListOf()
            }
        }
        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            when {
                line.matches(Regex("#{1,6}\\s+.+")) -> {
                    flushList()
                    val level = line.takeWhile { it == '#' }.length
                    val text = line.dropWhile { it == '#' }.trim()
                    Text(
                        remember(text) { Markdown.annotateInline(text, inlineCodeBg, linkColor) },
                        style = when (level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                line.matches(Regex("\\s*[-*+]\\s+.+")) -> {
                    listBuffer += line.trim().replaceFirst(Regex("[-*+]\\s+"), "")
                }
                line.matches(Regex("\\s*\\d+\\.\\s+.+")) -> {
                    listBuffer += line.trim().replaceFirst(Regex("\\d+\\.\\s+"), "")
                }
                line.startsWith(">") -> {
                    flushList()
                    Text(
                        remember(line) { Markdown.annotateInline(line.removePrefix(">").trim(), inlineCodeBg, linkColor) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                line.isBlank() -> {
                    flushList(); Spacer(Modifier.width(0.dp))
                }
                else -> {
                    flushList()
                    Text(
                        remember(line) { Markdown.annotateInline(line, inlineCodeBg, linkColor) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
        flushList()
    }
}
