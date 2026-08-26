package dev.opencode.android.runtime

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Checksums {
    fun sha256Hex(stream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 20)
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: File): String = file.inputStream().buffered().use { sha256Hex(it) }

    fun matches(expected: String, actual: String): Boolean =
        expected.trim().equals(actual.trim(), ignoreCase = true)
}
