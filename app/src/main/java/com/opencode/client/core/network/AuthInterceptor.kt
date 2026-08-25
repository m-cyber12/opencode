package com.opencode.client.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds authentication to outgoing requests.
 *
 * Two schemes, matching the two worlds the app talks to:
 *  - **Bearer** — gateway-managed workspaces (zero-setup cloud/self-host gateway).
 *  - **Basic**  — direct `opencode serve` connections protected by OPENCODE_SERVER_PASSWORD
 *    (developer mode).
 *
 * When a bearer token is present it always wins; basic credentials are the fallback.
 *
 * Credentials never appear in logs: this interceptor holds them only in memory.
 */
class AuthInterceptor(
    private val usernameProvider: () -> String = { "opencode" },
    private val passwordProvider: () -> String? = { null },
    private val bearerTokenProvider: () -> String? = { null }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = bearerTokenProvider()
        val password = passwordProvider()

        val request = when {
            !token.isNullOrBlank() -> chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()

            !password.isNullOrEmpty() -> {
                val user = usernameProvider().ifBlank { "opencode" }
                chain.request().newBuilder()
                    .header("Authorization", okhttp3.Credentials.basic(user, password))
                    .build()
            }

            else -> chain.request()
        }
        return chain.proceed(request)
    }
}

