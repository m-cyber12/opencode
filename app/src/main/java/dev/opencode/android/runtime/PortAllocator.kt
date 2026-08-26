package dev.opencode.android.runtime

import java.net.InetAddress
import java.net.ServerSocket

/**
 * Allocates a free loopback port by briefly binding a server socket.
 * The OpenCode server itself binds 127.0.0.1 only (spec §17).
 */
class PortAllocator(
    private val preferredRange: IntRange = 4096..4196,
) {
    data class Allocation(val port: Int)

    fun allocate(): Allocation {
        // Prefer the documented OpenCode default region, fall back to ephemeral.
        for (port in preferredRange) {
            if (isFree(port)) return Allocation(port)
        }
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { s ->
            return Allocation(s.localPort)
        }
    }

    fun isFree(port: Int): Boolean = try {
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).close()
        true
    } catch (_: Exception) {
        false
    }
}
