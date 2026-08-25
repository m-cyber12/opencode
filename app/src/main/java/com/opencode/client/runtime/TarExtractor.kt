package com.opencode.client.runtime

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal streaming extractor for POSIX ustar archives (.tar.gz) with NO third-party deps.
 *
 * Why hand-rolled: Android has no zstd/tar CLI we can rely on, and the build pipeline packs the
 * rootfs with `tar --format=ustar` precisely so this reader stays simple and dependency-free.
 * Handles: regular files, directories, symlinks, hardlinks (materialized as copies), ustar
 * prefix+name long paths. Unknown typeflags are skipped loudly (counted, not fatal).
 */
class TarExtractor(private val listener: ((extractedBytes: Long, totalBytes: Long) -> Unit)? = null) {

    data class Result(val files: Int, val dirs: Int, val symlinks: Int, val skipped: Int)

    fun extractGzipTar(gz: InputStream, destDir: File, totalBytes: Long): Result {
        var files = 0; var dirs = 0; var links = 0; var skipped = 0
        var extractedBytes = 0L

        GZIPInputStream(gz, 64 * 1024).use { tar ->
            val header = ByteArray(512)
            while (true) {
                if (!readFully(tar, header)) break // clean EOF between entries
                if (header.all { it == 0.toByte() }) continue // zero block padding

                val name = parseString(header, 0, 100)
                val prefix = parseString(header, 345, 155)
                val fullPath = if (prefix.isNotEmpty()) "$prefix/$name" else name
                val size = parseOctal(header, 124, 12)
                val typeFlag = (header[156].toInt().and(0xFF)).toChar()
                val linkName = parseString(header, 157, 100)
                val mode = parseOctal(header, 100, 8)

                val magicOk = String(header, 257, 5).startsWith("ustar")
                if (!magicOk) throw IOException("Not a ustar archive (bad magic at entry '$fullPath')")

                val target = File(destDir, fullPath).normalizeWithin(destDir)
                    ?: throw IOException("Entry escapes destination: $fullPath")

                when (typeFlag) {
                    '0', '\u0000' -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> copyExactly(tar, out, size) }
                        applyMode(target, mode)
                        files++
                        extractedBytes += size
                    }
                    '5' -> { target.mkdirs(); dirs++ }
                    '2' -> {
                        target.parentFile?.mkdirs()
                        target.delete()
                        java.nio.file.Files.createSymbolicLink(
                            target.toPath(), java.nio.file.Path.of(linkName)
                        )
                        links++
                        skip(tar, size)
                    }
                    '1' -> { // hardlink: materialize as copy from an earlier entry
                        val source = File(destDir, linkName).normalizeWithin(destDir)
                        if (source != null && source.exists()) {
                            target.parentFile?.mkdirs()
                            source.copyTo(target, overwrite = true)
                            files++
                            extractedBytes += size
                        } else skipped++
                        skip(tar, size)
                    }
                    else -> { skipped++; skip(tar, size) } // ustar format ⇒ no pax headers expected
                }

                listener?.invoke(extractedBytes, totalBytes)
            }
        }
        return Result(files, dirs, links, skipped)
    }

    // ---- helpers -----------------------------------------------------------------

    private fun File.normalizeWithin(root: File): File? {
        val canonicalRoot = root.canonicalFile
        val candidate = try { this.canonicalFile } catch (_: Exception) { return null }
        return if (candidate.path.startsWith(canonicalRoot.path)) candidate else null
    }

    private fun applyMode(file: File, mode: Int) {
        // Best-effort exec/permission preservation inside app-private storage.
        // Guest execution goes through proot anyway (empirical gate), so failures are non-fatal.
        runCatching {
            if (mode and 0b001_000_000 != 0) file.setExecutable(true, false)
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) {
                if (read == 0) return false // clean EOF between entries
                throw EOFException("truncated tar header")
            }
            read += n
        }
        return true
    }

    private fun copyExactly(input: InputStream, out: java.io.OutputStream, size: Long) {
        val buf = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) throw EOFException("truncated tar entry")
            out.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun skip(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val n = input.skip(remaining)
            if (n <= 0) throw EOFException("skip underflow")
            remaining -= n
        }
    }

    private fun parseString(header: ByteArray, off: Int, len: Int): String {
        var end = off
        val max = off + len
        while (end < max && header[end] != 0.toByte()) end++
        return String(header, off, end - off, Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun parseOctal(header: ByteArray, off: Int, len: Int): Int {
        var value = 0
        var started = false
        for (i in off until off + len) {
            val b = header[i].toInt() and 0xFF
            if (b == 0 || b == ' '.code) { if (started) break else continue }
            if (b < '0'.code || b > '7'.code) throw IOException("bad octal digit")
            started = true
            value = value shl 3 or (b - '0'.code)
        }
        return value
    }
}
