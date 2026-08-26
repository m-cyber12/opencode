package dev.opencode.android.runtime

import android.content.Context
import java.io.File

/**
 * Builds the PRoot command line and guest environment.
 *
 * Execution model (see versions.lock + docs/RUNTIME.md):
 *  - proot itself is executed from APK nativeLibraryDir (apk_data_file — executable
 *    at any targetSdk because useLegacyPackaging=true extracts real files).
 *  - Guest binaries live under app-private storage; exec on them is granted by the
 *    untrusted_app_27 SELinux domain (targetSdk 28) via execute_no_trans.
 */
class ProotLauncher(
    private val context: Context,
    private val installer: RuntimeInstaller,
    private val logs: LogRingBuffer,
) {
    data class LaunchSpec(
        val argv: List<String>,
        val env: Map<String, String>,
        val port: Int,
        val workingDirHost: File,
    )

    data class Inputs(
        val rootfsDir: File,
        val homeDir: File,
        val projectsDir: File,
        val tmpDir: File,
        val port: Int,
        val cwdGuestPath: String,
        val authContentJson: String?,
        val configContentJson: String?,
        val logLevelDebug: Boolean,
        val disableSeccomp: Boolean,
    )

    fun prootBinary(): File {
        val dir = context.applicationInfo.nativeLibraryDir
            ?: throw IllegalStateException("nativeLibraryDir unavailable")
        val f = File(dir, "libproot.so")
        if (!f.isFile) throw IllegalStateException("libproot.so missing from $dir — was useLegacyPackaging disabled?")
        return f
    }

    /** Pure arg/env construction — unit tested. */
    fun build(inputs: Inputs): LaunchSpec {
        val argv = buildList {
            add(prootBinary().absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("-0") // fake root inside guest
            add("-r"); add(inputs.rootfsDir.absolutePath)
            add("-w"); add(inputs.cwdGuestPath)
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("${inputs.homeDir.absolutePath}:/root")
            add("-b"); add("${inputs.projectsDir.absolutePath}:/projects")
            add("/usr/local/bin/opencode")
            add("serve")
            add("--hostname"); add("127.0.0.1")
            add("--port"); add(inputs.port.toString())
        }

        val env = buildMap {
            put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("HOME", "/root")
            put("TMPDIR", "/tmp")
            put("SHELL", "/bin/bash")
            put("TERM", "xterm-256color")
            put("LANG", "C.UTF-8")
            // proot host-side knobs:
            put("PROOT_TMP_DIR", inputs.tmpDir.absolutePath)
            if (inputs.disableSeccomp) put("PROOT_NO_SECCOMP", "1")
            // OpenCode provisioning (never persisted to disk in plaintext):
            if (!inputs.authContentJson.isNullOrBlank()) put("OPENCODE_AUTH_CONTENT", inputs.authContentJson)
            if (!inputs.configContentJson.isNullOrBlank()) put("OPENCODE_CONFIG_CONTENT", inputs.configContentJson)
            put("OPENCODE_DISABLE_AUTOUPDATE", "1")
            put("OPENCODE_LOG_LEVEL", if (inputs.logLevelDebug) "DEBUG" else "INFO")
            put("OPENCODE_CLIENT", "opencode-android")
        }

        return LaunchSpec(argv, env, inputs.port, inputs.tmpDir)
    }

    fun defaultInputs(
        port: Int,
        projectId: String?,
        authContentJson: String?,
        configContentJson: String?,
        disableSeccomp: Boolean,
        debugLogs: Boolean,
    ): Inputs {
        val base = installer.runtimeRoot()
        val home = File(context.filesDir, "home").apply { mkdirs() }
        val projects = File(context.filesDir, "projects").apply { mkdirs() }
        val tmp = File(base, "proot-tmp").apply { mkdirs() }
        return Inputs(
            rootfsDir = installer.rootfsDir(),
            homeDir = home,
            projectsDir = projects,
            tmpDir = tmp,
            port = port,
            cwdGuestPath = "/projects/${projectId ?: ""}".trimEnd('/').ifEmpty { "/" },
            authContentJson = authContentJson,
            configContentJson = configContentJson,
            logLevelDebug = debugLogs,
            disableSeccomp = disableSeccomp,
        )
    }

    fun spawn(spec: LaunchSpec): Process {
        logs.append("runtime", "Spawning opencode serve on 127.0.0.1:${spec.port}")
        val pb = ProcessBuilder(spec.argv).apply {
            environment().clear()
            environment().putAll(spec.env)
            redirectErrorStream(false)
        }
        return pb.start()
    }

    companion object {
        const val LIB_PROOT = "libproot.so"
    }
}
