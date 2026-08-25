package com.opencode.client.engine

import com.opencode.client.core.network.SseConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Abstraction over "the live event pipe" so demo mode drives the exact same parser pipeline
 * as a real SSE connection.
 */
interface EventTransport {
    sealed interface TransportState {
        data object Connecting : TransportState
        data object Live : TransportState
        data class Retrying(val attempt: Int, val nextInMs: Long) : TransportState
        data object Stopped : TransportState
    }

    val state: StateFlow<TransportState>
    val frames: SharedFlow<String>
    fun stop()
}

/** Real SSE transport against `<baseUrl>/global/event` with automatic reconnection. */
class SseEventTransport(
    url: String,
    client: OkHttpClient,
    scope: CoroutineScope
) : EventTransport {

    private val sse = SseConnection(url, client, scope)

    private val relayState = MutableStateFlow<EventTransport.TransportState>(EventTransport.TransportState.Connecting)
    override val state: StateFlow<EventTransport.TransportState> = relayState

    private val relayFrames = MutableSharedFlow<String>(extraBufferCapacity = 512)
    override val frames: SharedFlow<String> = relayFrames

    init {
        scope.launch {
            sse.state.collect { s ->
                relayState.value = when (s) {
                    is SseConnection.StreamState.Connecting -> EventTransport.TransportState.Connecting
                    is SseConnection.StreamState.Live -> EventTransport.TransportState.Live
                    is SseConnection.StreamState.Retrying ->
                        EventTransport.TransportState.Retrying(s.attempt, s.nextAttemptInMs)
                    is SseConnection.StreamState.Stopped -> EventTransport.TransportState.Stopped
                }
            }
        }
        scope.launch {
            sse.frames.collect { frame ->
                if (!relayFrames.tryEmit(frame.data)) {
                    // Backpressure safety: drop oldest is not available on plain tryEmit failure;
                    // events are idempotent for our reducer, dropping is safe.
                }
            }
        }
    }

    override fun stop() = sse.stop()
}
