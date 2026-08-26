package dev.opencode.android.runtime

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class ChecksumTest {
    @Test
    fun sha256Matches() {
        val data = "hello world".toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val actual = Checksums.sha256Hex(ByteArrayInputStream(data))
        assertEquals(expected, actual)
    }

    @Test
    fun matchesCaseInsensitive() {
        assertTrue(Checksums.matches("ABC", "abc"))
        assertFalse(Checksums.matches("ABC", "def"))
    }
}