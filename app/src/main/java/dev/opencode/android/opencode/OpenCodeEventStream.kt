package dev.opencode.android.opencode

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * SSE consumer for GET /event?directory=…
 *
 * Frame format (verified against server source, v1.18.x):
 *   event: message
 *   data: {"id":"evt_…","type":"…","properties":{…}}
 *
 * First frame: server.connected; heartbeat every 10s; terminal frame
 * server.instance.disposed → we reconnect automatically.
 */
class OpenCodeEventStream(
    private val baseUrl: String,
    private val directory: String?,
    private val onLog: (String) -> Unit = {},
    okHttpClient: OkHttpClient? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    sealed class Event {
        abstract val type: String
        abstract val properties: JsonObject

        data class Generic(override val type: String, override val properties: JsonObject, val raw: String) : Event()

        val sessionID: String? get() = (properties["sessionID"] as? JsonPrimitive)?.contentOrNull
    }

    private val http = okHttpClient ?: OpenCodeClient.newOkHttpForSse()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = scope
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    val events: SharedFlow<Event> get() = _events

    @Volatile
    private var running = false
    private var job: Job? = null
    private var attempt = 0

    fun start() {
        if (running) return
        running = true
        job = scope.launch { loop() }
    }

    fun stop() {
        running = false
        job?.cancel()
        scope.cancel()
    }

    private suspend fun loop() {
        while (running && kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                connectOnce()
                attempt = 0
            } catch (t: Throwable) {
                if (!running) break
                attempt++
                val waitMs = minOf(30_000L, 500L * (1L shl attempt.coerceAtMost(6)))
                onLog("event stream disconnected (${t.message}); reconnecting in ${waitMs}ms")
                delay(waitMs)
            }
        }
    }

    private suspend fun connectOnce() = withContext(Dispatchers.IO) {
        val urlBuilder = (baseUrl + "/event").toHttpUrl().newBuilder().apply {
            directory?.takeIf { it.isNotBlank() }?.let { addQueryParameter("directory", it) }
        }
        val req = Request.Builder().url(urlBuilder.build())
            .header("Accept", "text/event-stream")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("SSE HTTP ${resp.code}")
            val source = resp.body?.source() ?: throw IllegalStateException("SSE empty body")
            var eventName: String? = null
            while (running) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val data = line.removePrefix("data:").trim()
                        val parsed = parseFrame(data)
                        if (parsed != null) {
                            _events.emit(parsed)
                            // Terminal frame → force reconnect.
                            if (parsed.type == "server.instance.disposed" || parsed.type == "global.disposed") {
                                throw IllegalStateException("server disposed stream")
                            }
                        }
                        eventName = null
                    }
                    line.isEmpty() -> { /* frame boundary */ }
                }
            }
        }
    }

    private fun parseFrame(data: String): Event? {
        return try {
            val o = json.parseToJsonElement(data) as? JsonObject ?: return null
            val type = (o["type"] as? JsonPrimitive)?.contentOrNull ?: return null
            val props = o["properties"] as? JsonObject ?: JsonObject(emptyMap())
            Event.Generic(type, props, data)
        } catch (t: Throwable) {
            Log.w("OpenCodeEvents", "bad frame: ${t.message}")
            null
        }
    }
}
