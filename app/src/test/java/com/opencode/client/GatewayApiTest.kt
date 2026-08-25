package com.opencode.client

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.data.gateway.HttpGatewayApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Wire-contract tests for the open gateway spec (docs/GATEWAY.md). */
class GatewayApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpGatewayApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpGatewayApi(server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts credentials and parses token`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"token":"tok-42","email":"dev@example.com"}""")
                .setHeader("Content-Type", "application/json")
        )
        val res = api.login("Dev@Example.com", "hunter22")
        assertTrue(res is Outcome.Ok)
        assertEquals("tok-42", (res as Outcome.Ok).value.token)

        val recorded = server.takeRequest()
        assertEquals("/auth/login", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"email\":\"dev@example.com\"")) // normalized lowercase
        assertTrue(body.contains("\"password\":\"hunter22\""))
    }

    @Test
    fun `workspaces sends bearer and parses list`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"w1","name":"api-refactor","endpoint":"https://w1.gw.dev","status":"running","createdAt":1}]"""
            ).setHeader("Content-Type", "application/json")
        )
        val res = api.workspaces(token = "tok-42")
        val list = (res as Outcome.Ok).value
        assertEquals(1, list.size)
        assertEquals("api-refactor", list[0].name)
        assertEquals("running", list[0].status)

        assertEquals("Bearer tok-42", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `create workspace posts name with auth`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"w2","name":"fresh","endpoint":"https://w2.gw.dev","status":"creating","createdAt":2}"""
            )
        )
        val res = api.createWorkspace(token = "tok-9", name = "fresh")
        assertEquals("creating", (res as Outcome.Ok).value.status)

        val recorded = server.takeRequest()
        assertEquals("/workspaces", recorded.path)
        assertEquals("Bearer tok-9", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"name\":\"fresh\""))
    }

    @Test
    fun `delete returns true on success`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        val res = api.deleteWorkspace(token = "t", id = "w3")
        assertTrue(res is Outcome.Ok && res.value)
        assertEquals("/workspaces/w3", server.takeRequest().path)
    }

    @Test
    fun `401 surfaces as friendly auth error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"expired"}"""))
        val res = api.workspaces(token = "stale")
        val err = (res as Outcome.Err).error
        assertTrue(err is AppError.Http)
        assertTrue(err.userMessage.contains("Authentication", ignoreCase = true))
    }
}
