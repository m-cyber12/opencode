package dev.opencode.android.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns exactly one embedded OpenCode runtime process.
 *
 * Guarantees:
 *  - single-flight startup (no duplicate servers)
 *  - integrity-checked extraction before every cold start
 *  - health-gated HEALTHY state (/global/health)
 *  - crash detection + classified automatic restart with exponential backoff
 *  - graceful shutdown without zombie tracees (--kill-on-exit + destroy)
 */
class RuntimeManager(
    private val context: android.content.Context,
    private val installer: RuntimeInstaller,
    private val launcher: ProotLauncher,
    private val logs: LogRingBuffer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val portAllocator: PortAllocator = PortAllocator(),
    private val healthChecker: HealthChecker = HealthChecker(logs),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    val state: StateFlow<RuntimeState> get() = _state

    private val processRef = AtomicReference<Process?>(null)
    private var currentPort: AtomicInteger = AtomicInteger(-1)
    private var watcherJob: Job? = null
    private var disableSeccomp = false
    private var lastStartArgs: ProotLauncher.Inputs? = null

    /** Port persisted for reconnect-after-app-restart flows. */
    @Volatile
    var lastKnownPort: Int
        private set

    init {
        lastKnownPort = prefsFile().takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: -1
    }

    fun prefsFile(): File = File(context.filesDir, "runtime-port.txt")

    /**
     * Ensures a healthy runtime; idempotent.
     * Returns the RuntimeInfo when healthy, null otherwise (state carries failure detail).
     */
    suspend fun ensureStarted(inputsFactory: (Int) -> ProotLauncher.Inputs): RuntimeInfo? {
        // Fast path: already healthy.
        (_state.value as? RuntimeState.Healthy)?.let { return it.info }

        // Reconnect path: a previous server may still be alive after app restart.
        if (lastKnownPort > 0 && probeHealthy(lastKnownPort)) {
            logs.append("runtime", "Reconnected to existing server on port $lastKnownPort")
            val info = RuntimeInfo(
                port = lastKnownPort,
                baseUrl = "http://127.0.0.1:$lastKnownPort",
                pid = -1,
                rootfsDir = installer.rootfsDir().absolutePath,
                version = healthChecker.probe(lastKnownPort).version ?: "unknown",
            )
            _state.value = RuntimeState.Healthy(info)
            return info
        }

        return mutex.withLock {
            (_state.value as? RuntimeState.Healthy)?.let { return it.info }
            try {
                startLocked(inputsFactory)
            } catch (t: Throwable) {
                logs.append("runtime", "start failed: ${t.message}")
                _state.value = RuntimeState.Failed(humanize(t))
                null
            }
        }
    }

    suspend fun stop(reason: String = "user request") {
        val p = processRef.getAndSet(null) ?: run {
            _state.value = RuntimeState.Stopped
            return
        }
        _state.value = RuntimeState.Stopping(reason)
        watcherJob?.cancel()
        gracefulKill(p)
        currentPort.set(-1)
        lastKnownPort = -1
        prefsFile().delete()
        _state.value = RuntimeState.Stopped
        logs.append("runtime", "Runtime stopped ($reason)")
    }

    private suspend fun startLocked(inputsFactory: (Int) -> ProotLauncher.Inputs): RuntimeInfo? {
        if (!installer.isInstalled()) {
            _state.value = RuntimeState.Extracting(0f)
            withContext(Dispatchers.IO) {
                installer.installIfNeeded { f -> _state.value = RuntimeState.Extracting(f) }
            }
        } else {
            // Cheap re-validation of marker vs bundle (upgrade/corruption).
            val needsInstall = withContext(Dispatchers.IO) {
                installer.bundleSha256() != installer.installedSha256()
            }
            if (needsInstall) {
                _state.value = RuntimeState.Validating("Runtime update detected")
                withContext(Dispatchers.IO) {
                    installer.installIfNeeded { f -> _state.value = RuntimeState.Extracting(f) }
                }
            }
        }
        _state.value = RuntimeState.Validating("Verifying runtime integrity")
        if (!File(installer.rootfsDir(), RuntimeInstaller.BIN_OPENCODE).isFile) {
            throw IllegalStateException("opencode binary missing from extracted runtime")
        }

        val alloc = portAllocator.allocate()
        val inputs = inputsFactory(alloc.port)
        lastStartArgs = inputs
        _state.value = RuntimeState.Starting("Starting local agent on port ${alloc.port}")

        val proc = launcher.spawn(launcher.build(inputs))
        processRef.set(proc)
        currentPort.set(alloc.port)
        lastKnownPort = alloc.port
        prefsFile().parentFile?.mkdirs()
        prefsFile().writeText(alloc.port.toString())

        pumpLogs(proc)
        watchProcess(proc)

        _state.value = RuntimeState.WaitingHealth(1)
        val deadline = clock() + HEALTH_TIMEOUT_MS
        while (clock() < deadline) {
            if (!proc.isAlive) {
                // watcher will classify and restart
                throw IllegalStateException(processCrashMessage(proc))
            }
            val h = healthChecker.probe(alloc.port)
            if (h.healthy) {
                val info = RuntimeInfo(
                    port = alloc.port,
                    baseUrl = "http://127.0.0.1:${alloc.port}",
                    pid = proc.pid().toLong(),
                    rootfsDir = inputs.rootfsDir.absolutePath,
                    version = h.version ?: "unknown",
                )
                _state.value = RuntimeState.Healthy(info)
                logs.append("runtime", "OpenCode ${info.version} healthy on port ${info.port}")
                return info
            }
            delay(HEALTH_POLL_MS)
        }
        throw IllegalStateException("OpenCode server did not become healthy within ${HEALTH_TIMEOUT_MS / 1000}s")
    }

    private fun pumpLogs(proc: Process) {
        scope.launch {
            proc.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    logs.append("opencode", line)
                }
            }
        }
        scope.launch {
            proc.errorStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    logs.append("opencode.err", line)
                }
            }
        }
    }

    private fun watchProcess(proc: Process) {
        watcherJob?.cancel()
        watcherJob = scope.launch {
            val exit = proc.waitFor()
            if (_state.value.phase == RuntimePhase.STOPPING || _state.value.phase == RuntimePhase.STOPPED ||
                _state.value.phase == RuntimePhase.IDLE
            ) {
                return@launch
            }
            val stderrTail = logs.snapshot().takeLast(30).joinToString("\n") { it }
            val needsSeccompFallback = !disableSeccomp &&
                (exit != 0 && listOf("PROOT_NO_SECCOMP", "IS_IN_SYSENTER", "Assertion", "SIGSYS").any { stderrTail.contains(it) })
            if (needsSeccompFallback) {
                logs.append("runtime", "Detected seccomp/ptrace incompatibility; retrying with PROOT_NO_SECCOMP=1")
                disableSeccomp = true
                attemptRestart(exitCode = exit, attemptNumber = 1)
                return@launch
            }
            val attempt = restartAttempt.incrementAndGet()
            val decision = backoff.decisionFor(attempt)
            _state.value = RuntimeState.Crashed(
                exitCode = exit,
                restartAttempt = attempt,
                willRestart = decision.shouldRestart,
                detail = processCrashMessage(proc, exit),
            )
            if (decision.shouldRestart) {
                delay(decision.delayMs)
                attemptRestart(exitCode = exit, attemptNumber = attempt + 1)
            } else {
                _state.value = RuntimeState.Failed(
                    "OpenCode runtime stopped unexpectedly (exit $exit). Restarting the local agent failed after $attempt attempts.",
                )
            }
        }
    }

    private suspend fun attemptRestart(exitCode: Int, attemptNumber: Int) {
        mutex.lock()
        try {
            val factory = lastStartArgs?.let { prev -> { _: Int -> prev.copy(port = prev.port) } }
            if (factory == null) {
                _state.value = RuntimeState.Failed("Cannot restart: no previous launch parameters")
                return
            }
            logs.append("runtime", "Auto-restart attempt #$attemptNumber (previous exit=$exitCode)")
            _state.value = RuntimeState.Starting("Restarting local agent…")
            startLocked(factory)
        } catch (t: Throwable) {
            _state.value = RuntimeState.Failed(humanize(t))
        } finally {
            mutex.unlock()
        }
    }

    private val restartAttempt = AtomicInteger(0)

    fun resetRestartCounter() {
        restartAttempt.set(0)
    }

    private fun processCrashMessage(proc: Process, code: Int = -1): String = when {
        code == 126 -> "Runtime binary could not be executed on this device (ABI/linker mismatch)."
        (code and 0xff) == 137 || (code and 0x7f) == 9 -> "Runtime was killed by the OS (likely memory pressure)."
        else -> "The local agent exited unexpectedly (code $code)."
    }

    private fun humanize(t: Throwable): String = when {
        t.message?.contains("did not become healthy") == true ->
            "OpenCode started but did not respond in time. The device may be under heavy load — try again."
        t is java.io.IOException -> "Could not start the local agent: ${t.message}"
        else -> "Local agent error: ${t.message ?: t.javaClass.simpleName}"
    }

    private fun probeHealthy(port: Int): Boolean = healthChecker.probe(port).healthy

    private fun gracefulKill(p: Process) {
        try {
            p.destroy()
            val deadline = clock() + KILL_GRACE_MS
            while (p.isAlive && clock() < deadline) Thread.sleep(50)
            if (p.isAlive) {
                logs.append("runtime", "Force-killing runtime process tree")
                p.destroyForcibly()
            }
        } catch (e: Exception) {
            logs.append("runtime", "kill issue: ${e.message}")
        }
    }

    companion object {
        const val HEALTH_TIMEOUT_MS = 120_000L
        const val HEALTH_POLL_MS = 750L
        const val KILL_GRACE_MS = 8_000L
    }
}
