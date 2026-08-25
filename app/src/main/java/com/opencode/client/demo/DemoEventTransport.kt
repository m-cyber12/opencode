package com.opencode.client.demo

import com.opencode.client.engine.EventTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * EventTransport implementation that replays [DemoRuntime]'s scripted SSE frames.
 * The production parser consumes these identically to real server frames.
 */
class DemoEventTransport(private val scope: CoroutineScope) : EventTransport {

    private val stateRelay = MutableStateFlow<EventTransport.TransportState>(EventTransport.TransportState.Connecting)
    override val state: StateFlow<EventTransport.TransportState> = stateRelay

    private val framesRelay = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    override val frames: SharedFlow<String> = framesRelay

    private var job: Job? = null

    init {
        job = scope.launch {
            stateRelay.value = EventTransport.TransportState.Connecting
            // Brief "connecting" beat so connection UX is visible in demo mode.
            kotlinx.coroutines.delay(350)
            stateRelay.value = EventTransport.TransportState.Live
            DemoRuntime.seedInitialSessions()
            framesRelay.emit("""{"type":"server.connected","properties":{}}""")

            DemoRuntime.emitter.collect { frame ->
                framesRelay.emit(frame)
            }
        }
        // Bridge ServerController blips (reconnect simulation) through our state relay.
        DemoRuntime.connectionBlipListener = {
            stateRelay.value = EventTransport.TransportState.Retrying(1, 1200)
            scope.launch {
                kotlinx.coroutines.delay(1300)
                if (stateRelay.value !is EventTransport.TransportState.Stopped) {
                    stateRelay.value = EventTransport.TransportState.Live
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        stateRelay.value = EventTransport.TransportState.Stopped
    }
}
