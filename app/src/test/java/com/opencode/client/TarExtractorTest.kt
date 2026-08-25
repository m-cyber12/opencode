package com.opencode.client

import com.opencode.client.runtime.TarExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The ustar reader is the only thing standing between the bundled rootfs and the guest
 * filesystem - so its contract is pinned here with a hand-built archive.
 */
class TarExtractorTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun header(
        name: String,
        size: Long,
        type: Char,
        mode: Int = 0b110_100_100, // 0644
        linkName: String = "",
        prefix: String = ""
    ): ByteArray {
        val h = ByteArray(512)
        fun put(str: String, off: Int, len: Int) {
            val bytes = str.toByteArray(Charsets.UTF_8)
            check(bytes.size <= len) { "field overflow: $str" }
            bytes.copyInto(h, off)
        }
        // ustar splits long paths into prefix(155) + name(100).
        if (name.length > 100) {
            val splitAt = name.lastIndexOf('/', name.length - 100).coerceAtLeast(0)
            put(name.substring(0, splitAt), 345, 155)          // prefix
            put(name.substring(splitAt + 1), 0, 100)           // name
        } else {
            put(name, 0, 100)
        }
        put("%07o".format(mode), 100, 8)
        put("%011o".format(0L), 108, 12)   // uid
        put("%011o".format(0L), 116, 12)   // gid
        put("%011o".format(size), 124, 12)
        put("%011o".format(0L), 136, 12)   // mtime
        put("        ", 148, 8)            // checksum placeholder (parser ignores it)
        h[156] = type.code.toByte()
        if (linkName.isNotEmpty()) put(linkName, 157, 100)
        put("ustar", 257, 6)
        put("00", 263, 2)
        return h
    }

    private fun entry(headerBytes: ByteArray, content: ByteArray = ByteArray(0)): ByteArray {
        // Compute real checksum so stricter readers could also consume this fixture.
        val h = headerBytes.clone()
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        var sum = 0
        for (b in h) sum += b.toInt() and 0xFF
        "%06o\u0000 ".format(sum).toByteArray(Charsets.ISO_8859_1).copyInto(h, 148)

        val blocks = ByteArrayOutputStream()
        blocks.write(h)
        blocks.write(content)
        val pad = ((512 - content.size % 512) % 512)
        blocks.write(ByteArray(pad))
        return blocks.toByteArray()
    }

    private fun tarOf(vararg entries: ByteArray, trailingZeroBlocks: Int = 2): ByteArray =
        entries.fold(ByteArrayOutputStream()) { acc, e -> acc.write(e); acc }
            .let { baos ->
                repeat(trailingZeroBlocks) { baos.write(ByteArray(512)) }
                baos.toByteArray()
            }

    private fun gz(data: ByteArray) = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(data) }
    }.toByteArray()

    @Test
    fun `extracts regular file with content and exec bit`() {
        val dest = tmp.newFolder("rootfs")
        val content = "#!/bin/sh\necho hi\n".toByteArray()
        val result = TarExtractor().extractGzipTar(
            ByteArrayInputStream(gz(tarOf(entry(header("run.sh", content.size.toLong(), '0', mode = 0b111_101_101), content)))),
            dest,
            totalBytes = 1
        )
        assertEquals(1, result.files)
        val f = dest.resolve("run.sh")
        assertTrue(f.isFile)
        assertEquals(content.decodeToString(), f.readText())
        assertTrue("exec bit lost", f.canExecute())
    }

    @Test
    fun `long path via prefix field is reconstructed`() {
        val dest = tmp.newFolder("rootfs")
        val longPath = "home/opencode/.bun/install/global/node_modules/" +
            "some-deeply/nested/package/with/a/really/long/path/index.js"
        val content = "export {}".toByteArray()
        TarExtractor().extractGzipTar(
            ByteArrayInputStream(gz(tarOf(entry(header(longPath, content.size.toLong(), '0'), content)))),
            dest,
            totalBytes = 1
        )
        assertTrue(dest.resolve(longPath).readText() == "export {}")
    }

    @Test
    fun `symlinks are created`() {
        val dest = tmp.newFolder("rootfs")
        TarExtractor().extractGzipTar(
            ByteArrayInputStream(gz(tarOf(entry(header("usr/local/bin/bunx", 0, '2', linkName = "/usr/local/bin/bun"))))),
            dest,
            totalBytes = 1
        )
        val link = dest.resolve("usr/local/bin/bunx")
        assertTrue(java.nio.file.Files.isSymbolicLink(link.toPath()))
        assertEquals("/usr/local/bin/bun", java.nio.file.Files.readSymbolicLink(link.toPath()).toString())
    }

    @Test
    fun `directories and nested files land correctly`() {
        val dest = tmp.newFolder("rootfs")
        val content = "x".toByteArray()
        TarExtractor().extractGzipTar(
            ByteArrayInputStream(gz(tarOf(
                entry(header("etc", 0, '5')),
                entry(header("etc/resolv.conf", content.size.toLong(), '0'), content)
            ))),
            dest,
            totalBytes = 1
        )
        assertTrue(dest.resolve("etc").isDirectory)
        assertEquals("x", dest.resolve("etc/resolv.conf").readText())
    }

    @Test
    fun `path traversal entries are rejected`() {
        val dest = tmp.newFolder("rootfs")
        val evil = tarOf(entry(header("../escaped.txt", 5, '0'), "evil!".toByteArray()))
        try {
            TarExtractor().extractGzipTar(ByteArrayInputStream(gz(evil)), dest, totalBytes = 1)
            org.junit.Assert.fail("expected traversal rejection")
        } catch (_: java.io.IOException) { /* expected */ }
    }

    @Test
    fun `gzip stream is required`() {
        try {
            TarExtractor().extractGzipTar(ByteArrayInputStream("not gzip".toByteArray()), tmp.newFolder(), 1)
            org.junit.Assert.fail("expected IOException for non-gzip input")
        } catch (_: java.io.IOException) { /* expected */ }
    }
}
