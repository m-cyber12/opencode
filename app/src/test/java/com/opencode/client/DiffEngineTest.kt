package com.opencode.client

import com.opencode.client.engine.DiffEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {

    @Test
    fun `identical content yields no hunks`() {
        val hunks = DiffEngine.computeUnified("a\nb\nc", "a\nb\nc")
        assertTrue(hunks.isEmpty())
    }

    @Test
    fun `simple line modification produces one add and one del`() {
        val hunks = DiffEngine.computeUnified(
            before = "line1\nline2\nline3",
            after = "line1\nCHANGED\nline3"
        )
        assertEquals(1, hunks.size)
        val lines = hunks[0].lines
        assertTrue(lines.any { it.kind == com.opencode.client.domain.LineKind.DEL && it.text == "line2" })
        assertTrue(lines.any { it.kind == com.opencode.client.domain.LineKind.ADD && it.text == "CHANGED" })
        // Context preserved around the change.
        assertTrue(lines.any { it.kind == com.opencode.client.domain.LineKind.CONTEXT && it.text == "line1" })
        assertTrue(lines.any { it.kind == com.opencode.client.domain.LineKind.CONTEXT && it.text == "line3" })
        // Line numbers are populated on context/del/add sides respectively.
        val ctx = lines.first { it.kind == com.opencode.client.domain.LineKind.CONTEXT }
        assertEquals(1, ctx.oldNo)
        assertEquals(1, ctx.newNo)
        val del = lines.first { it.kind == com.opencode.client.domain.LineKind.DEL }
        assertEquals(2, del.oldNo)
        assertEquals(null, del.newNo)
    }

    @Test
    fun `pure addition keeps old numbering intact`() {
        val hunks = DiffEngine.computeUnified(
            before = "a\nb",
            after = "a\nNEW\nb"
        )
        val all = hunks.flatMap { it.lines }
        val added = all.first { it.kind == com.opencode.client.domain.LineKind.ADD }
        assertEquals("NEW", added.text)
        assertEquals(null, added.oldNo)
        assertEquals(2, added.newNo)
    }

    @Test
    fun `changes separated by many context lines produce two hunks`() {
        val before = (1..30).joinToString("\n") { "row$it" }
        val afterLines = (1..30).map { if (it == 2) "ROW" else if (it == 28) "ROW" else "row$it" }
        val hunks = DiffEngine.computeUnified(before, afterLines.joinToString("\n"))
        assertEquals(2, hunks.size)
    }

    @Test
    fun `oversized middle section falls back to replace block without crash`() {
        val before = (1..3000).joinToString("\n") { "x" }
        val after = (1..3000).joinToString("\n") { "y" }
        val hunks = DiffEngine.computeUnified(before, after)
        assertTrue(hunks.isNotEmpty())
        val kinds = hunks.flatMap { it.lines }.map { it.kind }.toSet()
        assertTrue(kinds.contains(com.opencode.client.domain.LineKind.DEL))
        assertTrue(kinds.contains(com.opencode.client.domain.LineKind.ADD))
    }

    @Test
    fun `empty to content is a single add hunk`() {
        val hunks = DiffEngine.computeUnified("", "hello\nworld")
        assertEquals(1, hunks.size)
        val adds = hunks[0].lines.filter { it.kind == com.opencode.client.domain.LineKind.ADD }
        assertEquals(listOf("hello", "world"), adds.map { it.text })
    }

    @Test
    fun `trailing newline handling stays stable`() {
        val hunks = DiffEngine.computeUnified("a\n", "a")
        // Only a phantom trailing difference - acceptable either as empty or one del; must not crash.
        assertTrue(hunks.size <= 1)
    }
}
