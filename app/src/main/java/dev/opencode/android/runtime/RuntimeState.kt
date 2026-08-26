package dev.opencode.android.runtime

enum class RuntimePhase { IDLE, EXTRACTING, VALIDATING, STARTING, WAITING_HEALTH, HEALTHY, STOPPING, STOPPED, CRASHED, FAILED }

data class RuntimeInfo(
    val port: Int,
    val baseUrl: String,
    val pid: Long,
    val rootfsDir: String,
    val version: String,
)

/**
 * Finite state machine for the embedded runtime.
 * All transitions funnel through [transition] so tests can assert the full
 * lifecycle and UI can render a single source of truth.
 */
sealed class RuntimeState {
    abstract val phase: RuntimePhase
    open val detail: String? = null
    open val info: RuntimeInfo? = null

    data object Idle : RuntimeState() { override val phase = RuntimePhase.IDLE }
    data class Extracting(val progress: Float) : RuntimeState() { override val phase = RuntimePhase.EXTRACTING }
    data class Validating(override val detail: String) : RuntimeState() { override val phase = RuntimePhase.VALIDATING }
    data class Starting(override val detail: String) : RuntimeState() { override val phase = RuntimePhase.STARTING }
    data class WaitingHealth(val attempt: Int) : RuntimeState() { override val phase = RuntimePhase.WAITING_HEALTH }
    data class Healthy(override val info: RuntimeInfo) : RuntimeState() {
        override val phase = RuntimePhase.HEALTHY
        override val detail get() = "127.0.0.1:${info.port} (opencode ${info.version})"
    }
    data class Stopping(override val detail: String?) : RuntimeState() { override val phase = RuntimePhase.STOPPING }
    data object Stopped : RuntimeState() { override val phase = RuntimePhase.STOPPED }
    data class Crashed(
        val exitCode: Int,
        val restartAttempt: Int,
        val willRestart: Boolean,
        override val detail: String?,
    ) : RuntimeState() { override val phase = RuntimePhase.CRASHED }
    data class Failed(override val detail: String) : RuntimeState() { override val phase = RuntimePhase.FAILED }
}
