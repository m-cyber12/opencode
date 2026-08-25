package com.opencode.client

import com.opencode.client.opencode.event.OpenCodeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Event parser contract tests. The server is a moving target; unknown shapes must NEVER crash.
 */
class OpenCodeEventParserTest {

    @Test
    fun `parses instance-shaped event`() {
        val data = """
            {"type":"session.idle","properties":{"sessionID":"ses-1"}}
        """.trimIndent()
        val parsed = OpenCodeEvent.parse(data)
        assertEquals("ses-1", (parsed.event as OpenCodeEvent.SessionIdle).sessionID)
        assertNull(parsed.directory)
    }

    @Test
    fun `parses global-shaped event and unwraps directory`() {
        val data = """
            {"directory":"/repo/x","payload":{"type":"file.edited","properties":{"file":"a.kt"}}}
        """.trimIndent()
        val parsed = OpenCodeEvent.parse(data)
        assertEquals("/repo/x", parsed.directory)
        assertEquals("a.kt", (parsed.event as OpenCodeEvent.FileEdited).file)
    }

    @Test
    fun `message part updated with delta decodes tool part`() {
        val data = """
            {"type":"message.part.updated","properties":{
              "part":{"id":"p1","sessionID":"s","messageID":"m1","type":"tool","callID":"c1","tool":"bash",
                "state":{"status":"completed","input":{"command":"ls"},"output":"src\nbuild.gradle.kts",
                  "title":"Ran ls","metadata":{"exitCode":0},"time":{"start":1,"end":2}}},
              "delta":"src"}}
        """.trimIndent()
        val parsed = OpenCodeEvent.parse(data)
        val ev = parsed.event as OpenCodeEvent.MessagePartUpdated
        assertEquals("tool", ev.part.type)
        assertEquals("bash", ev.part.tool)
        assertEquals("completed", ev.part.state?.status)
        assertEquals("src", ev.delta)
    }

    @Test
    fun `permission updated decodes patterns and metadata options`() {
        val data = """
            {"type":"permission.updated","properties":{"permission":{
              "id":"perm1","type":"bash","pattern":"git *","sessionID":"s","messageID":"m",
              "title":"Run git reset --hard","metadata":{"options":["Allow","Reject"]},
              "time":{"created":123}}}}
        """.trimIndent()
        val ev = OpenCodeEvent.parse(data).event as OpenCodeEvent.PermissionUpdated
        assertEquals("perm1", ev.permission.id)
        assertEquals(listOf("git *"), ev.permission.pattern)
        assertNotNull(ev.permission.metadata)
    }

    @Test
    fun `unknown future event becomes Unknown not crash`() {
        val parsed = OpenCodeEvent.parse(
            """{"type":"brandnew.thing.v99","properties":{"x":1,"nested":{"deep":[1,2,3]}}}"""
        )
        val ev = parsed.event
        assertTrue(ev is OpenCodeEvent.Unknown)
        assertEquals("brandnew.thing.v99", ev.type)
    }

    @Test
    fun `malformed frames are skipped safely`() {
        assertNull(OpenCodeEvent.parse("").takeIf { it.event !is OpenCodeEvent.Ignored })
        assertTrue(OpenCodeEvent.parse("not json at all").event is OpenCodeEvent.Ignored)
        assertTrue(OpenCodeEvent.parse("""{"noType":true}""").event is OpenCodeEvent.Unknown)
        // Array instead of object:
        assertTrue(OpenCodeEvent.parse("[1,2,3]").event is OpenCodeEvent.Ignored)
    }

    @Test
    fun `session error extracts nested message from data`() {
        val parsed = OpenCodeEvent.parse(
            """{"type":"session.error","properties":{"sessionID":"s","error":
               {"name":"ProviderAuthError","data":{"providerID":"anthropic","message":"expired"}}}}"""
        )
        val ev = parsed.event as OpenCodeEvent.SessionError
        assertEquals("ProviderAuthError", ev.errorName)
        assertEquals("expired", ev.errorMessage)
        assertEquals("s", ev.sessionID)
    }

    @Test
    fun `todo updated decodes list`() {
        val parsed = OpenCodeEvent.parse(
            """{"type":"todo.updated","properties":{"sessionID":"s","todos":[
               {"id":"1","content":"step one","status":"completed","priority":"high"}]}}"""
        )
        val ev = parsed.event as OpenCodeEvent.TodoUpdated
        assertEquals(1, ev.todos.size)
        assertEquals("step one", ev.todos[0].content)
    }
}
