package com.opencode.client.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Long-lived SSE connection with automatic reconnection using exponential backoff + jitter.
 *
 * Frames are exposed through a hot SharedFlow so multiple consumers (chat engines, session list,
 * activity log) can subscribe independently. Unknown/malformed frames are forwarded verbatim;
 * parsing happens downstream where they are safely ignored.
 */
class SseConnection(
    url: String,
    callFactory: OkHttpClient,
    private val scope: CoroutineScope
) {
    sealed interface StreamState {
        data object Connecting : StreamState
        data object Live : StreamState
        data class Retrying(val attempt: Int, val nextAttemptInMs: Long) : StreamState
        data object Stopped : StreamState
    }

    data class Frame(val event: String?, val data: String)

    private val request: Request = Request.Builder()
        .url(url)
        .header("Accept", "text/event-stream")
        .header("Cache-Control", "no-cache")
        .build()

    // No read timeout: SSE connections may stay quiet between agent turns.
    private val sseClient: OkHttpClient = callFactory.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val factory = EventSources.createFactory(sseClient)

    private val _state = MutableStateFlow<StreamState>(StreamState.Connecting)
    val state: StateFlow<StreamState> = _state

    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 512)
    val frames: SharedFlow<Frame> = _frames

    @Volatile
    private var stopped = false

    private var sourceRef: EventSource? = null
    private var loopJob: Job? = null

    init {
        loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (!stopped && scope.isActive) {
            _state.value = StreamState.Connecting
            val opened = kotlinx.coroutines.CompletableDeferred<Boolean>()
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    attempt = 0
                    _state.value = StreamState.Live
                    opened.complete(true)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (!stopped) {
                        _frames.tryEmit(Frame(type, data))
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    if (!closed.isCompleted) closed.complete(Unit)
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    if (!opened.isCompleted) opened.complete(false)
                    if (!closed.isCompleted) closed.complete(Unit)
                }
            }

            sourceRef = factory.newEventSource(request, listener)
            val ok = opened.await()
            if (ok && !stopped) {
                // Stream is live; wait until it closes/fails.
                closed.await()
            } else if (!stopped) {
                // Never opened - wait a moment before counting the failure.
                delay(250)
            }
            try {
                sourceRef?.cancel()
            } catch (_: Exception) {
            }

            if (stopped) break

            attempt += 1
            val backoffMs = computeBackoff(attempt)
            _state.value = StreamState.Retrying(attempt, backoffMs)
            delay(backoffMs)
        }
        _state.value = StreamState.Stopped
    }

    private fun computeBackoff(attempt: Int): Long {
        val base = (1000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
        return (base * 0.8 + Random.nextLong(base.coerceAtLeast(1)) * 0.4).toLong()
            .coerceIn(500L, 45_000L)
    }

    /** Idempotent. Cancels the HTTP stream and stops the reconnect loop. */
    fun stop() {
        stopped = true
        try {
            sourceRef?.cancel()
        } catch (_: Exception) {
        }
        loopJob?.cancel()
        _state.value = StreamState.Stopped
    }
}
