package dev.opencode.android.ui.components

import dev.opencode.android.ui.components.DiffParser.Line
import org.junit.Assert.*
import org.junit.Test

class DiffParserTest {
    @Test
    fun parsesUnifiedDiff() {
        val diff = """--- a/file.txt
+++ b/file.txt
@@ -1,3 +1,4 @@
 context line
-removed line
+added line
 context line2"""
        val lines = DiffParser.parse(diff)
        assertEquals(4, lines.size)
        assertEquals(Line.Kind.CONTEXT, lines[0].kind)
        assertEquals(Line.Kind.DEL, lines[1].kind)
        assertEquals(Line.Kind.ADD, lines[2].kind)
        assertEquals(Line.Kind.CONTEXT, lines[3].kind)
    }

    @Test
    fun ignoresHeaderLines() {
        val diff = """--- a/file.txt
+++ b/file.txt
@@ -1 +1 @@
+only added"""
        val lines = DiffParser.parse(diff)
        assertEquals(1, lines.size)
        assertEquals(Line.Kind.ADD, lines[0].kind)
    }
}