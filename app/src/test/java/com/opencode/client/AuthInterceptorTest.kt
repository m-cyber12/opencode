package com.opencode.client

import com.opencode.client.core.network.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun hit(client: OkHttpClient): String? {
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().use { }
        return server.takeRequest().getHeader("Authorization")
    }

    @Test
    fun `bearer token wins when present`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    usernameProvider = { "opencode" },
                    passwordProvider = { "pw" },
                    bearerTokenProvider = { "tok-1" }
                )
            ).build()
        assertEquals("Bearer tok-1", hit(client))
    }

    @Test
    fun `basic auth used with username and password`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    usernameProvider = { "alice" },
                    passwordProvider = { "s3cret" }
                )
            ).build()
        // base64("alice:s3cret")
        assertEquals("YWxpY2U6czNjcmV0", hit(client)?.removePrefix("Basic "))
    }

    @Test
    fun `no credentials means no header`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .build()
        assertNull(hit(client))
    }
}
