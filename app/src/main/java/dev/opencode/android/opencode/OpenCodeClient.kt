package dev.opencode.android.opencode

import Api.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client for the on-device OpenCode server.
 * Instance-scoped routes are routed by the `x-opencode-directory` header.
 * Loopback only — never exposed off-device (spec §17/§19).
 */
class OpenCodeClient(
    private val baseUrlProvider: () -> String?,
    okHttpClient: OkHttpClient? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // prompt POST awaits full agent turn
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    var directoryHeader: String? = null

    private fun base(): String =
        baseUrlProvider() ?: throw IOException("OpenCode runtime is not running yet")

    private fun req(method: String, path: String, body: RequestBody? = null, directory: String? = directoryHeader): Request.Builder {
        val url = (base() + path).toHttpUrl()
        val b = Request.Builder().url(url).method(method, body ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON))
        directory?.takeIf { it.isNotBlank() }?.let { b.header("x-opencode-directory", it) }
        return b
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for ${request.url.encodedPath}: ${text.take(300)}")
            text
        }
    }

    // ---- health / global ----

    suspend fun globalHealth(): Api.Health {
        val text = call(req("GET", "/global/health", directory = null))
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrDefault(JsonObject(emptyMap()))
        return Api.Health(
            healthy = (o["healthy"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
            version = o.str("version"),
        )
    }

    suspend fun disposeAll(): Boolean {
        call(req("POST", "/global/dispose", directory = null))
        return true
    }

    // ---- sessions ----

    suspend fun listSessions(directory: String? = directoryHeader): List<Api.SessionInfo> {
        val url = (base() + "/session").toHttpUrl().newBuilder().apply {
            directory?.let { addQueryParameter("directory", it) }
        }.build()
        val text = call(Request.Builder().url(url).build())
        val arr = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonArray(emptyList())) as? JsonArray ?: JsonArray(emptyList())
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            Api.SessionInfo(
                id = o.str("id") ?: return@mapNotNull null,
                title = o.str("title"),
                directory = o.str("directory"),
                projectID = o.str("projectID"),
                parentID = o.str("parentID"),
                raw = o,
            )
        }
    }

    suspend fun createSession(title: String?, directory: String? = directoryHeader): Api.SessionInfo {
        val body = buildJsonObject {
            title?.let { put("title", it) }
        }.toString().toRequestBody(JSON)
        val text = call(req("POST", "/session", body, directory))
        val o = json.parseToJsonElement(text) as JsonObject
        return Api.SessionInfo(
            id = o.str("id")!!,
            title = o.str("title"),
            directory = o.str("directory"),
            projectID = o.str("projectID"),
            parentID = o.str("parentID"),
            raw = o,
        )
    }

    suspend fun deleteSession(id: String): Boolean {
        call(req("DELETE", "/session/$id"))
        return true
    }

    suspend fun messages(sessionId: String, limit: Int = 200, directory: String? = directoryHeader): List<Api.MessageWithParts> {
        val url = (base() + "/session/$sessionId/message").toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { directory?.let { addQueryParameter("directory", it) } }
            .build()
        val text = call(Request.Builder().url(url).build())
        val arr = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonArray(emptyList())) as? JsonArray ?: JsonArray(emptyList())
        return arr.map { el ->
            val o = el as JsonObject
            Api.MessageWithParts(
                info = o["info"] as? JsonObject ?: JsonObject(emptyMap()),
                parts = (o["parts"] as? JsonArray)?.toList() ?: emptyList(),
            )
        }
    }

    /**
     * Sends a user prompt. The POST returns the final assistant turn; live
     * streaming arrives via the SSE event stream (see EventStream).
     */
    suspend fun prompt(sessionId: String, text: String, providerID: String?, modelID: String, agent: String = "build", attachments: List<Attachment> = emptyList()): Api.MessageWithParts {
        val body = buildJsonObject {
            agent.takeIf { it.isNotBlank() }?.let { put("agent", it) }
            if (!providerID.isNullOrBlank()) {
                put("model", buildJsonObject {
                    put("providerID", providerID)
                    put("modelID", modelID)
                })
            }
            putJsonArray("parts") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
                attachments.forEach { a ->
                    add(buildJsonObject {
                        put("type", "file")
                        put("mime", a.mime)
                        put("filename", a.filename)
                        put("url", a.url)
                    })
                }
            }
        }.toString().toRequestBody(JSON)
        val respText = call(req("POST", "/session/$sessionId/message", body))
        val o = runCatching { json.parseToJsonElement(respText) as? JsonObject }.getOrNull()
            ?: return Api.MessageWithParts(JsonObject(emptyMap()), emptyList())
        return Api.MessageWithParts(
            info = o["info"] as? JsonObject ?: JsonObject(emptyMap()),
            parts = (o["parts"] as? JsonArray)?.toList() ?: emptyList(),
        )
    }

    suspend fun abortSession(sessionId: String): Boolean {
        call(req("POST", "/session/$sessionId/abort"))
        return true
    }

    /** Initializes a git repository in the routed project directory (guest git). */
    suspend fun gitInit(directory: String? = directoryHeader): Boolean {
        val body = buildJsonObject {
            directory?.let { put("directory", it) }
        }.toString().toRequestBody(JSON)
        call(req("POST", "/project/git/init", body, directory = null))
        return true
    }

    // ---- permissions ----

    suspend fun pendingPermissions(directory: String? = directoryHeader): List<Api.PermissionRequest> {
        val url = (base() + "/permission").toHttpUrl().newBuilder()
            .apply { directory?.let { addQueryParameter("directory", it) } }.build()
        val text = call(Request.Builder().url(url).build())
        val arr = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonArray(emptyList())) as? JsonArray ?: JsonArray(emptyList())
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            Api.PermissionRequest(
                id = o.str("id") ?: return@mapNotNull null,
                sessionID = o.str("sessionID") ?: "",
                permission = o.str("permission") ?: "unknown",
                patterns = (o["patterns"] as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: emptyList(),
                metadata = o["metadata"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
    }

    /** reply ∈ {"once","always","reject"} */
    suspend fun replyPermission(requestId: String, reply: String, message: String? = null): Boolean {
        val body = buildJsonObject {
            put("reply", reply)
            message?.let { put("message", it) }
        }.toString().toRequestBody(JSON)
        call(req("POST", "/permission/$requestId/reply", body, directory = null))
        return true
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        data class Attachment(val mime: String, val filename: String, val url: String)

        fun newOkHttpForSse(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

private fun kotlinx.serialization.json.JsonElement.JsonObject_orEmpty(): JsonObject =
    this as? JsonObject ?: JsonObject(emptyMap())
