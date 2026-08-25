package com.opencode.client

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.opencode.HttpOpenCodeApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the HTTP client against realistic server payloads (MockWebServer).
 * These pin the wire contract: paths, query parameters, auth header, and decoding behavior.
 */
class HttpOpenCodeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpOpenCodeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient()
        api = HttpOpenCodeApi.forServer(server.url("/").toString().trimEnd('/'), client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health decodes version`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"healthy":true,"version":"v1.2.3"}""")
                .setHeader("Content-Type", "application/json")
        )
        val result = api.health()
        assertTrue(result is Outcome.Ok)
        assertEquals("v1.2.3", (result as Outcome.Ok).value.version)
        assertEquals("/global/health", server.takeRequest().path)
    }

    @Test
    fun `sessions request includes directory query parameter`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        api.sessions(directory = "/repo/demo")
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.startsWith("/session"))
        assertTrue(path.contains("directory=%2Frepo%2Fdemo") || path.contains("directory=/repo/demo"))
    }

    @Test
    fun `prompt_async posts parts json and tolerates 204 empty body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = api.promptAsync(
            id = "ses-9",
            body = com.opencode.client.opencode.dto.PromptBodyDto(
                model = com.opencode.client.opencode.dto.ModelRefDto("anthropic", "claude-sonnet-4"),
                agent = "build",
                parts = listOf(
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                        put("text", kotlinx.serialization.json.JsonPrimitive("hello"))
                    }
                )
            ),
            directory = null
        )
        assertTrue(result is Outcome.Ok)
        val recorded = server.takeRequest()
        assertEquals("/session/ses-9/prompt_async", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"providerID\":\"anthropic\""))
        assertTrue(body.contains("\"text\":\"hello\""))
    }

    @Test
    fun `permission response sends once-always-reject enum value`() = runBlocking {
        server.enqueue(MockResponse().setBody("true"))
        api.respondToPermission(id = "ses-1", permissionID = "perm-2", response = "once")
        val recorded = server.takeRequest()
        assertEquals("/session/ses-1/permissions/perm-2", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"response\":\"once\""))
    }

    @Test
    fun `http 401 maps to friendly Auth error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"message":"bad credentials"}""")
        )
        val result = api.health()
        assertTrue(result is Outcome.Err)
        val err = (result as Outcome.Err).error
        assertTrue(err is AppError.Http)
        assertTrue(err.userMessage.contains("Authentication", ignoreCase = true))
    }

    @Test
    fun `server 500 surfaces internal-error copy with technical body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom trace"))
        val result = api.sessions()
        val err = (result as Outcome.Err).error
        assertTrue(err.userMessage.contains("internal error", ignoreCase = true))
        assertTrue(err.technical?.contains("boom trace") == true)
    }

    @Test
    fun `unknown fields in session payload are ignored`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"s1","title":"T","projectID":"p","directory":"/d","version":"v",
                     "futureField":{"nested":[1,2,3]},"time":{"created":10,"updated":20}}]"""
            )
        )
        val result = api.sessions()
        val sessions = (result as Outcome.Ok).value
        assertEquals(1, sessions.size)
        assertEquals("T", sessions[0].title)
        assertEquals(20L, sessions[0].time.updated)
    }

    @Test
    fun `file content decodes text type and diff`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"type":"text","content":"println(1)","diff":"@@ -1 +1 @@\n-a\n+b"}"""
            )
        )
        val result = api.readFile(path = "src/A.kt", directory = null)
        val file = (result as Outcome.Ok).value
        assertFalse(file.binary)
        assertEquals("println(1)", file.content)
        assertTrue(file.diff!!.contains("@@"))
    }
}
