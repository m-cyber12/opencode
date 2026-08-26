package dev.opencode.android.ui.markdown

import dev.opencode.android.ui.markdown.Markdown.Block
import org.junit.Assert.*
import org.junit.Test

class MarkdownTest {
    @Test
    fun splitsCodeAndTextBlocks() {
        val md = """Text before
```kotlin
fun main() { println("hi") }
```
Text after"""
        val blocks = Markdown.splitBlocks(md)
        assertEquals(3, blocks.size)
        assertEquals(Block.Kind.TEXT, blocks[0].kind)
        assertEquals(Block.Kind.CODE, blocks[1].kind)
        assertEquals("kotlin", blocks[1].lang)
        assertEquals(Block.Kind.TEXT, blocks[2].kind)
    }

    @Test
    fun handlesUnclosedFence() {
        val md = """Text
```kotlin
code without close"""
        val blocks = Markdown.splitBlocks(md)
        // Should treat as text since no closing fence
        assertEquals(1, blocks.size)
        assertEquals(Block.Kind.TEXT, blocks[0].kind)
    }
}