package dev.opencode.android.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Probes the local OpenCode server.
 * Health endpoint: GET /global/health → {"healthy":true,"version":"1.18.x"}
 */
class HealthChecker(
    private val logs: LogRingBuffer,
    client: OkHttpClient? = null,
) {
    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class Health(val healthy: Boolean, val version: String?)

    fun probe(port: Int): Health {
        return try {
            val req = Request.Builder()
                .url("http://127.0.0.1:$port/global/health")
                .header("User-Agent", "opencode-android-runtime")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Health(false, null)
                val body = resp.body?.string() ?: return Health(false, null)
                val obj = json.parseToJsonElement(body).jsonObject
                val ok = obj["healthy"]?.jsonPrimitive?.booleanOrNull ?: false
                val version = obj["version"]?.jsonPrimitive?.content
                Health(ok, version)
            }
        } catch (e: IOException) {
            Health(false, null)
        } catch (e: Exception) {
            logs.append("health", "unexpected probe error: ${e.message}")
            Health(false, null)
        }
    }
}
