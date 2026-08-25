package com.opencode.client

import com.opencode.client.runtime.RuntimeEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the exact proot invocation that gates G1-G3 execute. If these tests fail after a change,
 * the change altered the experiment - bump the gate expectations deliberately, never silently.
 */
class RuntimeEnvTest {

    private val proot = File("/data/app/x/lib/arm64/libopx-proot.so")
    private val loader = File("/data/app/x/lib/arm64/libopx-proot-loader.so")
    private val rootfs = File("/data/data/com.opencode.client/files/runtime/rootfs")

    @Test
    fun `argv shape is the proot contract under test`() {
        val cmd = RuntimeEnv.build(
            proot = proot,
            prootLoader = loader,
            rootfsDir = rootfs,
            guestWorkdir = "/home/opencode/project",
            hostLogDir = File("/data/data/com.opencode.client/files/runtime/logs"),
            port = 4096
        )

        assertEquals(proot.absolutePath, cmd.argv.first())
        assertTrue(cmd.argv.contains("-R"))
        assertEquals(rootfs.absolutePath, cmd.argv[cmd.argv.indexOf("-R") + 1])
        assertTrue(cmd.argv.contains("-0"))                       // uid-0 inside private guest
        assertTrue(cmd.argv.contains("-w"))
        assertTrue(cmd.argv.contains("--kill-on-exit"))

        // The serve command must bind LOOPBACK ONLY (never LAN) on the chosen port.
        val serveLine = cmd.argv.last()
        assertTrue(serveLine.contains("opencode-serve serve --hostname 127.0.0.1 --port 4096"))
    }

    @Test
    fun `loader path is exported for seccomp-sensitive OEMs`() {
        val cmd = RuntimeEnv.build(proot, loader, rootfs, "/home/opencode/project", File("/tmp"), 4096)
        assertEquals(loader.absolutePath, cmd.env["PROOT_LOADER"])
        assertEquals("1", cmd.env["PROOT_NO_SECCOMP"])
    }

    @Test
    fun `guest command is injectable for one-shot probes`() {
        val cmd = RuntimeEnv.build(
            proot, loader, rootfs, "/home/opencode/project", File("/tmp"), 1,
            guestCommand = "echo probe"
        )
        assertEquals("echo probe", cmd.argv.last())
        assertEquals("/bin/sh", cmd.argv[cmd.argv.size - 3])
        assertEquals("-lc", cmd.argv[cmd.argv.size - 2])
    }

    @Test
    fun `port picker returns a port inside its scan range`() {
        val port = RuntimeEnv.pickFreePort(start = 45000)
        assertTrue(port in 45000 until 45032)
        // Availability was validated by the picker's own exclusive bind probe.
    }
}
