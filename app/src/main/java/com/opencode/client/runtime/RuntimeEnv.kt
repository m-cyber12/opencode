package com.opencode.client.runtime

import java.io.File

/**
 * Pure builder for the proot invocation. No Android imports — fully unit-tested.
 *
 * The argv/env produced here IS the hypothesis executed by gates G1..G3; if those fail,
 * this file is where the exact command line under test lives.
 */
object RuntimeEnv {

    data class Command(val argv: List<String>, val env: Map<String, String>, val port: Int)

    fun build(
        proot: File,
        prootLoader: File,
        rootfsDir: File,
        guestWorkdir: String,
        hostLogDir: File,
        port: Int,
        guestCommand: String = defaultServeCommand(port)
    ): Command {
        val env = mapOf(
            "PROOT_LOADER" to prootLoader.absolutePath,
            // Empirical: prior art needed seccomp tolerance on some OEMs; harmless elsewhere.
            "PROOT_NO_SECCOMP" to "1",
            "PATH" to "${proot.parentFile?.absolutePath}:/system/bin:/system/xbin",
            "HOME" to rootfsDir.resolve("home/opencode").absolutePath,
            "TMPDIR" to File(rootfsDir, "tmp").absolutePath,
            "LD_LIBRARY_PATH" to (proot.parentFile?.absolutePath ?: "")
        )
        val argv = listOf(
            proot.absolutePath,
            "-R", rootfsDir.absolutePath,
            "-0",                                  // pretend uid 0 inside the private guest
            "-w", guestWorkdir,
            "-b", "/proc",
            "-b", "/dev",
            "-b", "/sys",
            "--kill-on-exit",
            "/bin/sh", "-lc",
            guestCommand
        )
        return Command(argv, env + mapOf("OPX_LOG_DIR" to hostLogDir.absolutePath), port)
    }

    fun defaultServeCommand(port: Int): String =
        "mkdir -p \"\$HOME\" /tmp && exec /usr/local/bin/opencode-serve serve --hostname 127.0.0.1 --port $port"

    /** Picks a free loopback port deterministically starting at OpenCode's default. */
    fun pickFreePort(start: Int = 4096, attempts: Int = 32): Int {
        repeat(attempts) { idx ->
            val candidate = start + idx
            try {
                java.net.ServerSocket().use { s ->
                    s.bind(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), candidate))
                    return candidate
                }
            } catch (_: java.net.BindException) { /* try next */ }
        }
        error("no free loopback port in [$start, ${start + attempts})")
    }
}
