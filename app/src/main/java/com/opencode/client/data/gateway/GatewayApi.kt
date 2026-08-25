package com.opencode.client.data.gateway

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.core.appJson
import com.opencode.client.core.network.Http
import kotlinx.serialization.Serializable
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Client for the OpenCode Gateway: the thin, open service contract that turns "opencode serve"
 * into a zero-setup product (accounts + workspace provisioning + per-workspace proxying).
 *
 * The full contract lives in docs/GATEWAY.md. It is intentionally tiny and self-hostable;
 * nothing proprietary is assumed, and no secrets ever ship inside the APK.
 */

@Serializable
data class GatewayWorkspaceDto(
    val id: String,
    val name: String,
    /** Absolute https URL of the OpenCode server proxied for this workspace. */
    val endpoint: String,
    val status: String = "unknown",          // creating | running | stopped | error
    val createdAt: Long = 0
)

@Serializable
data class GatewayLoginResponseDto(
    val token: String,
    val email: String? = null
)

/** Result of asking the gateway for a usable connection profile. */
data class WorkspaceConnection(
    val workspaceId: String,
    val name: String,
    val baseUrl: String,
    val bearerToken: String?
)

interface GatewayApi {
    suspend fun login(email: String, password: String): Outcome<GatewayLoginResponseDto>
    suspend fun register(email: String, password: String): Outcome<GatewayLoginResponseDto>
    suspend fun workspaces(token: String): Outcome<List<GatewayWorkspaceDto>>
    suspend fun createWorkspace(token: String, name: String): Outcome<GatewayWorkspaceDto>
    suspend fun deleteWorkspace(token: String, id: String): Outcome<Boolean>
}

class HttpGatewayApi(
    private val gatewayBaseUrl: String
) : GatewayApi {

    private val callFactory = okhttp3.OkHttpClient()

    private fun url(path: String) = gatewayBaseUrl.trimEnd('/') + path

    private inline fun <reified T> call(request: okhttp3.Request): Outcome<T> =
        Http.execute(callFactory, request) { response ->
            appJson.decodeFromString<T>(response.body?.string().orEmpty())
        }

    private fun post(path: String, body: String, token: String? = null): okhttp3.Request {
        val builder = okhttp3.Request.Builder()
            .url(url(path))
            .post(body.toRequestBody(Http.JSON_MEDIA))
        token?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private fun get(path: String, token: String): okhttp3.Request =
        okhttp3.Request.Builder()
            .url(url(path))
            .get()
            .header("Authorization", "Bearer $token")
            .build()

    private fun delete(path: String, token: String): okhttp3.Request =
        okhttp3.Request.Builder()
            .url(url(path))
            .delete()
            .header("Authorization", "Bearer $token")
            .build()

    override suspend fun login(email: String, password: String): Outcome<GatewayLoginResponseDto> {
        if (gatewayBaseUrl.isBlank()) return Outcome.Err(AppError.NotConnected())
        val body = appJson.encodeToString(
            LoginRequest.serializer(), LoginRequest(email.trim().lowercase(), password)
        )
        return call(post("/auth/login", body))
    }

    override suspend fun register(email: String, password: String): Outcome<GatewayLoginResponseDto> {
        if (gatewayBaseUrl.isBlank()) return Outcome.Err(AppError.NotConnected())
        val body = appJson.encodeToString(
            LoginRequest.serializer(), LoginRequest(email.trim().lowercase(), password)
        )
        return call(post("/auth/register", body))
    }

    override suspend fun workspaces(token: String): Outcome<List<GatewayWorkspaceDto>> {
        if (gatewayBaseUrl.isBlank()) return Outcome.Err(AppError.NotConnected())
        return call(get("/workspaces", token))
    }

    override suspend fun createWorkspace(token: String, name: String): Outcome<GatewayWorkspaceDto> {
        if (gatewayBaseUrl.isBlank()) return Outcome.Err(AppError.NotConnected())
        val body = appJson.encodeToString(
            CreateWorkspaceRequest.serializer(), CreateWorkspaceRequest(name)
        )
        return call(post("/workspaces", body, token))
    }

    override suspend fun deleteWorkspace(token: String, id: String): Outcome<Boolean> =
        call(delete("/workspaces/$id", token)).map { true }
}

@kotlinx.serialization.Serializable
private data class LoginRequest(val email: String, val password: String)

@kotlinx.serialization.Serializable
private data class CreateWorkspaceRequest(val name: String)
