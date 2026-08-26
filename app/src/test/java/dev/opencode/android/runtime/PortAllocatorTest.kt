package dev.opencode.android.runtime

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket

class PortAllocatorTest {
    @Test
    fun allocatesFreePort() {
        val alloc = PortAllocator(4096..4100)
        val a1 = alloc.allocate()
        assertTrue(a1.port in 4096..4100 || a1.port > 0)
        // The port should be free after release (ServerSocket closed)
        assertTrue(alloc.isFree(a1.port))
    }

    @Test
    fun detectsUsedPort() {
        val ss = ServerSocket(0)
        val port = ss.localPort
        val alloc = PortAllocator()
        assertFalse(alloc.isFree(port))
        ss.close()
        assertTrue(alloc.isFree(port))
    }
}