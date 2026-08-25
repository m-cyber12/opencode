package com.opencode.client

import com.opencode.client.core.text.SyntaxHighlighter
import com.opencode.client.core.text.SyntaxHighlighter.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun `kotlin keywords strings and comments are distinguished`() {
        val code = "// header\nfun main() {\n  val s = \"hi\"\n}"
        val spans = SyntaxHighlighter.tokenize(code, "kotlin")
        fun kindAt(index: Int): TokenKind? =
            spans.firstOrNull { index >= it.start && index < it.end }?.kind
        val commentIdx = code.indexOf("// header")
        val keywordIdx = code.indexOf("val ")
        val stringIdx = code.indexOf("\"hi\"")

        assertEquals(TokenKind.COMMENT, kindAt(commentIdx))
        assertEquals(TokenKind.KEYWORD, kindAt(keywordIdx))
        assertEquals(TokenKind.STRING, kindAt(stringIdx))
    }

    @Test
    fun `spans cover the entire input without gaps`() {
        val code = "class A { /* c */ val x = 1.5e3; return null }"
        val spans = SyntaxHighlighter.tokenize(code, "kotlin")
        var cursor = 0
        for (span in spans) {
            assertEquals(cursor, span.start)
            assertTrue(span.end > span.start)
            cursor = span.end
        }
        assertEquals(code.length, cursor)
    }

    @Test
    fun `unknown language yields single base span`() {
        val spans = SyntaxHighlighter.tokenize("whatever !@# content", "cobol-not-mapped")
        assertEquals(1, spans.size)
        assertEquals(TokenKind.BASE, spans[0].kind)
    }

    @Test
    fun `file extension maps to languages`() {
        assertEquals("kotlin", SyntaxHighlighter.languageForFile("src/App.kt"))
        assertEquals("typescript", SyntaxHighlighter.languageForFile("index.tsx"))
        assertEquals("bash", SyntaxHighlighter.languageForFile("run.sh"))
        assertEquals(null, SyntaxHighlighter.languageForFile("README"))
    }

    @Test
    fun `fence tag mapping is forgiving`() {
        assertEquals("python", SyntaxHighlighter.languageForFenceTag("py"))
        assertEquals("bash", SyntaxHighlighter.languageForFenceTag("shell"))
        assertEquals(null, SyntaxHighlighter.languageForFenceTag(""))
    }
}
