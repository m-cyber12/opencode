package com.opencode.client.core.text

/**
 * Lightweight, dependency-free syntax highlighter.
 *
 * Produces token spans for a useful subset of languages (C-family, Kotlin, Python, shell, JSON,
 * XML/HTML, diff). It is intentionally approximate - it exists to make code readable on a phone,
 * not to pass compiler tests. Unknown extensions fall back to plain text with strings/comments
 * heuristics disabled.
 */
object SyntaxHighlighter {

    enum class TokenKind { BASE, KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION, FUNCTION }

    data class Span(val start: Int, val end: Int, val kind: TokenKind)

    private data class LangSpec(
        val lineComments: List<String>,
        val blockCommentStart: String?,
        val blockCommentEnd: String?,
        val keywords: Set<String>,
        val hashComments: Boolean = false,
        val tripleQuotes: Boolean = false
    )

    private val KW_COMMON = setOf(
        "if", "else", "for", "while", "return", "break", "continue", "new", "try", "catch",
        "finally", "throw", "switch", "case", "default", "do", "in", "is", "as", "when", "then"
    )

    private val specs: Map<String, LangSpec> = mapOf(
        "kotlin" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf(
                "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum",
                "companion", "private", "public", "internal", "protected", "override", "open",
                "abstract", "suspend", "import", "package", "this", "super", "null", "true",
                "false", "const", "lateinit", "init", "typealias", "inline", "operator", "vararg",
                "out", "reified", "where", "by", "get", "set", "constructor", "expect", "actual"
            )
        ),
        "java" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf(
                "class", "interface", "enum", "record", "extends", "implements", "import",
                "package", "public", "private", "protected", "static", "final", "void", "int",
                "long", "double", "float", "boolean", "char", "byte", "short", "null", "true",
                "false", "this", "super", "abstract", "synchronized", "volatile", "transient"
            )
        ),
        "swift" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf("func", "let", "var", "class", "struct", "protocol", "extension", "guard", "nil", "self", "static", "import")
        ),
        "rust" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf("fn", "let", "mut", "pub", "struct", "impl", "trait", "enum", "use", "mod", "match", "Some", "None", "Ok", "Err", "crate", "self", "Self")
        ),
        "go" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf("func", "package", "import", "var", "const", "type", "struct", "interface", "map", "chan", "go", "defer", "range", "nil", "select")
        ),
        "python" to LangSpec(
            listOf("#"), null, null,
            KW_COMMON + setOf("def", "class", "lambda", "import", "from", "pass", "with", "yield", "global", "nonlocal", "assert", "raise", "del", "None", "True", "False", "elif", "not", "and", "or", "async", "await", "except"),
            hashComments = true, tripleQuotes = true
        ),
        "javascript" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf("function", "const", "let", "var", "class", "extends", "import", "export", "from", "async", "await", "typeof", "instanceof", "null", "undefined", "true", "false", "of", "delete", "void")
        ),
        "typescript" to LangSpec(
            listOf("//"), "/*", "*/",
            KW_COMMON + setOf("function", "const", "let", "var", "class", "extends", "implements", "interface", "type", "enum", "namespace", "declare", "readonly", "public", "private", "protected", "abstract", "async", "await", "null", "undefined", "true", "false", "keyof", "infer")
        ),
        "bash" to LangSpec(
            listOf("#"), null, null,
            setOf("if", "then", "else", "fi", "for", "in", "do", "done", "while", "case", "esac", "function", "local", "export", "echo", "cd", "exit", "return", "source", "set"),
            hashComments = true
        ),
        "json" to LangSpec(
            emptyList(), null, null,
            setOf("true", "false", "null")
        ),
        "xml" to LangSpec(
            emptyList(), "<!--", "-->", emptySet()
        )
    )

    fun languageForFile(nameOrPath: String): String? {
        val name = nameOrPath.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "swift" -> "swift"
            "rs" -> "rust"
            "go" -> "go"
            "py" -> "python"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "sh", "bash", "zsh" -> "bash"
            "json" -> "json"
            "xml", "html", "htm", "svg", "plist" -> "xml"
            else -> null
        }
    }

    fun languageForFenceTag(tag: String): String? {
        val t = tag.trim().lowercase()
        return when {
            t.isEmpty() -> null
            t in setOf("kotlin", "kts") -> "kotlin"
            t in setOf("java") -> "java"
            t in setOf("python", "py") -> "python"
            t in setOf("js", "javascript") -> "javascript"
            t in setOf("ts", "typescript") -> "typescript"
            t in setOf("bash", "shell", "sh", "zsh") -> "bash"
            t == "json" -> "json"
            t in setOf("xml", "html", "svg") -> "xml"
            t == "go" -> "go"
            t == "rust" || t == "rs" -> "rust"
            t == "swift" -> "swift"
            else -> null
        }
    }

    /** Tokenizes [code] written in [language] into ordered, non-overlapping spans covering 0..length. */
    fun tokenize(code: String, language: String?): List<Span> {
        if (code.isEmpty()) return emptyList()
        val spec = language?.let { specs[it] } ?: return listOf(Span(0, code.length, TokenKind.BASE))
        val out = ArrayList<Span>()
        var i = 0
        val n = code.length

        while (i < n) {
            // Block comments
            if (spec.blockCommentStart != null && code.startsWith(spec.blockCommentStart, i)) {
                val end = code.indexOf(spec.blockCommentEnd!!, i + spec.blockCommentStart.length)
                val stop = if (end == -1) n else end + spec.blockCommentEnd.length
                out.add(Span(i, stop, TokenKind.COMMENT)); i = stop; continue
            }
            // Line comments
            val lc = spec.lineComments.firstOrNull { code.startsWith(it, i) }
            if (lc != null) {
                val nl = code.indexOf('\n', i)
                val stop = if (nl == -1) n else nl
                out.add(Span(i, stop, TokenKind.COMMENT)); i = stop; continue
            }
            // Strings (triple first)
            if (spec.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i))) {
                val quote = code.substring(i, i + 3)
                val end = code.indexOf(quote, i + 3)
                val stop = if (end == -1) n else end + 3
                out.add(Span(i, stop, TokenKind.STRING)); i = stop; continue
            }
            val c = code[i]
            if (c == '"' || c == '\'' || c == '`') {
                var j = i + 1
                while (j < n) {
                    if (code[j] == '\\' && j + 1 < n) { j += 2; continue }
                    if (code[j] == c) { j++; break }
                    if (code[j] == '\n' && c != '`') break // unterminated on this line
                    j++
                }
                out.add(Span(i, j.coerceAtMost(n), TokenKind.STRING)); i = j; continue
            }
            // Annotations / decorators / XML tags
            if ((c == '@') && language != "json") {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                if (j > i + 1) {
                    out.add(Span(i, j, TokenKind.ANNOTATION)); i = j; continue
                }
            }
            // Numbers
            if (c.isDigit()) {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' ||
                            ((code[j] == '+' || code[j] == '-') && j > i && (code[j - 1] == 'e' || code[j - 1] == 'E')))
                ) j++
                out.add(Span(i, j, TokenKind.NUMBER)); i = j; continue
            }
            // Identifiers / keywords / function calls
            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                val kind = when {
                    spec.keywords.contains(word) -> TokenKind.KEYWORD
                    j < n && code[j] == '(' -> TokenKind.FUNCTION
                    word[0].isUpperCase() -> TokenKind.FUNCTION
                    else -> TokenKind.BASE
                }
                out.add(Span(i, j, kind)); i = j; continue
            }

            // Everything else: merge consecutive base chars.
            var j = i + 1
            while (j < n) {
                val d = code[j]
                if (d.isLetter() || d.isDigit() || d == '_' || d == '"' || d == '\'' || d == '`' ||
                    d == '@' || d == '#' || startsAny(code, j, spec)
                ) break
                j++
            }
            out.add(Span(i, j, TokenKind.BASE)); i = j
        }
        return out
    }

    private fun startsAny(code: String, at: Int, spec: LangSpec): Boolean {
        if (code[at] == '/' && spec.lineComments.any { it.startsWith("/") } &&
            at + 1 < code.length && (code[at + 1] == '/' || code[at + 1] == '*')
        ) return true
        if (spec.hashComments && code[at] == '#') return true
        return false
    }
}
