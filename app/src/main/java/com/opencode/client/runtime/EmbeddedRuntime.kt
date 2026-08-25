package com.opencode.client.runtime

import com.opencode.client.core.native.NativeArtifacts
import com.opencode.client.core.network.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Owns the local OpenCode process: proot → guest sh → `opencode serve` on 127.0.0.1.
 *
 * State machine: IDLE → BOOTSTRAPPING → STARTING → HEALTHY, with CRASHED + automatic restart
 * (bounded backoff). Gates G3/G6/G7/G8 exercise this class directly on an emulator.
 */
class EmbeddedRuntime(
    private val context: android.content.Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val autoRestart: Boolean = true,
    private val maxRestartAttempts: Int = 5,
    private val healthTimeoutMs: Long = 240_000L // cold bun+opencode under proot+emulator is slow
) {

    sealed interface State {
        data object Idle : State
        data class Bootstrapping(val stage: RootfsBootstrap.Stage, val fraction: Float) : State
        data object Starting : State
        data class Healthy(val port: Int) : State
        data class Crashed(val attempts: Int, val reason: String) : State
        data object Stopped : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var process: Process? = null

    /** Populated once healthy; gates and the UI read the endpoint from here. */
    @Volatile
    var baseUrl: String? = null
        private set

    private val restarting = AtomicBoolean(false)

    fun bootstrapNow(): Boolean {
        if (_state.value !is State.Idle && _state.value !is State.Stopped &&
            _state.value !is State.Crashed
        ) return false
        val boot = RootfsBootstrap(context) { stage, fraction ->
            _state.value = State.Bootstrapping(stage, fraction)
        }
        val extracted = boot.ensureExtracted()
        _state.value = State.Idle
        return extracted
    }

    /**
     * Starts (or restarts) the runtime and suspends until /global/health reports healthy.
     * @throws RuntimeException on timeout or repeated crash - surfaced verbatim to gates/UI.
     */
    suspend fun start(): String {
        when (val s = NativeArtifacts.resolve(context)) {
            is NativeArtifacts.Status.Missing ->
                error("runtime artifacts missing: ${s.missing} (CI must place them before build)")
            else -> Unit
        }

        stopInternal(restartPending = true)
        _state.value = State.Starting

        val port = RuntimeEnv.pickFreePort()
        val artifacts = (NativeArtifacts.resolve(context) as NativeArtifacts.Status.Ready).artifacts
        val layout = RootfsBootstrap(context).layout()
        layout.logDir.mkdirs()

        val cmd = RuntimeEnv.build(
            proot = artifacts.proot,
            prootLoader = artifacts.prootLoader,
            rootfsDir = layout.rootfsDir,
            guestWorkdir = "/home/opencode/project",
            hostLogDir = layout.logDir,
            port = port
        )

        val errLog = File(layout.logDir, "opencode-stderr.log")
        val pb = ProcessBuilder(cmd.argv).apply {
            environment().putAll(cmd.env)
            redirectErrorStream(false)
            redirectError(errLog)
        }
        val started = try {
            pb.start()
        } catch (e: Exception) {
            restarting.set(false)
            enterCrash(attempt = 0, reason = "exec failed: ${e.message}")
            throw IllegalStateException("Failed to exec proot (${cmd.argv.first()}): ${e.message}", e)
        }
        process = started
        restarting.set(false) // from here on, an unexpected death IS a crash

        // Watchdog: if the process dies while we wait for health, fail fast into crash path.
        scope.launch {
            started.waitFor()
            if (_state.value !is State.Healthy && !restarting.get()) {
                val tail = errLog.takeIfExists(2000)
                enterCrash(attempt = currentAttempt(), reason = tail ?: "process exited early")
            }
        }

        awaitHealth(port)
        attempts = 0
        baseUrl = "http://127.0.0.1:$port"
        _state.value = State.Healthy(port)
        return baseUrl!!
    }

    suspend fun stop() {
        stopInternal(restartPending = false)
        _state.value = State.Stopped
        baseUrl = null
    }

    /** Gate G7 hook: simulate a hard crash of the runtime process (SIGKILL semantics). */
    fun crashForTesting() {
        check(process != null) { "runtime not started" }
        restarting.set(false) // let the watchdog classify this as a real crash → auto-restart
        process?.destroyForcibly()
    }

    private suspend fun awaitHealth(port: Int) {
        val deadline = System.currentTimeMillis() + healthTimeoutMs
        val client = Http.client(com.opencode.client.core.network.AuthInterceptor())
        val url = "http://127.0.0.1:$port/global/health"
        while (System.currentTimeMillis() < deadline) {
            val proc = process ?: throw IllegalStateException("process died before health")
            if (!proc.isAlive) {
                val tail = File(
                    RootfsBootstrap(context).layout().logDir, "opencode-stderr.log"
                ).takeIfExists(2000)
                error("guest process exited (code=${proc.exitValue()}): $tail")
            }
            runCatching {
                Http.execute(client, Http.get(url)) { it.string() }.let { res ->
                    if (res is com.opencode.client.core.Outcome.Ok) {
                        val healthy = runCatching {
                            com.opencode.client.core.appJson.decodeFromString(
                                com.opencode.client.opencode.dto.HealthDto.serializer(),
                                res.value
                            ).healthy
                        }.getOrDefault(false)
                        if (healthy) return
                    }
                }
            }
            delay(500)
        }
        error("health check timed out after ${healthTimeoutMs}ms")
    }

    private fun stopInternal(restartPending: Boolean) {
        restarting.set(restartPending)
        process?.destroy()
        // Give proot a moment; SIGKILL if it lingers (proot forwards signals to guests).
        val p = process
        if (p != null) {
            Thread {
                runCatching {
                    if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
                }
            }.apply { isDaemon = true }.start()
        }
        process = null
        baseUrl = null
    }

    private var attempts = 0

    private fun currentAttempt(): Int = attempts

    private fun enterCrash(attempt: Int, reason: String) {
        _state.value = State.Crashed(attempts = attempt, reason = reason.take(500))
        if (autoRestart && attempt < maxRestartAttempts && !restarting.get()) {
            scope.launch {
                delay(backoffMs(attempt))
                attempts += 1
                restarting.set(false)
                runCatching { start() }
            }
        }
    }

    private fun backoffMs(attempt: Int): Long =
        min(1_000L shl min(attempt, 5), 30_000L)

    private fun File.takeIfExists(maxChars: Int): String? =
        takeIf { exists() }?.readText()?.takeLast(maxChars)?.ifBlank { null }
}
