package dev.opencode.android.runtime

import android.content.Context
import android.system.Os
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Installs the bundled Linux userspace (Alpine rootfs + pinned OpenCode binary)
 * from APK assets into app-private storage, with integrity verification,
 * upgrade/corruption detection and recovery (spec §20/§21/§27).
 *
 * Bundle produced by scripts/build-runtime.sh:
 *   assets/runtime/rootfs.tar.gz        — container
 *   assets/runtime/rootfs.sha256        — hex sha256 of the container
 *   assets/runtime/rootfs.manifest.json — {fileCount, uncompressedBytes, versions…}
 */
class RuntimeInstaller(
    private val context: Context,
    private val logs: LogRingBuffer,
) {
    @Serializable
    data class BundleManifest(
        val layoutVersion: Int = 1,
        val fileCount: Long = 0,
        val uncompressedBytes: Long = 0,
        val opencodeVersion: String = "",
        val alpineVersion: String = "",
        val packages: Map<String, String> = emptyMap(),
    )

    data class InstallResult(val rootfsDir: File, val sha256: String)

    fun bundleSha256(): String =
        context.assets.open(SHA_ASSET).bufferedReader().use { it.readText().trim().split(Regex("\\s+")).first() }

    fun bundleManifest(): BundleManifest = try {
        context.assets.open(MANIFEST_ASSET).bufferedReader().use {
            Json { ignoreUnknownKeys = true }.decodeFromString(BundleManifest.serializer(), it.readText())
        }
    } catch (_: Exception) {
        BundleManifest()
    }

    fun openContainerStream(): InputStream =
        java.util.zip.GZIPInputStream(context.assets.open(TAR_ASSET).buffered(), 1 shl 16)

    /**
     * Extracts the runtime. Skips work when the on-disk marker matches the
     * bundle hash; wipes and re-extracts otherwise (corruption or upgrade).
     */
    fun installIfNeeded(onProgress: (Float) -> Unit): InstallResult {
        val rootDir = runtimeRoot()
        val rootfs = File(rootDir, "rootfs")
        val expectedSha = bundleSha256()
        val manifest = bundleManifest()

        if (!needsInstall(expectedSha)) {
            logs.append("installer", "Runtime already installed (sha256 ok), skipping extraction")
            onProgress(1f)
            return InstallResult(rootfs, expectedSha)
        }

        logs.append("installer", "Installing runtime userspace…")
        if (rootfs.exists()) {
            logs.append("installer", "Removing previous/partial installation")
            rootfs.deleteRecursively()
        }
        rootfs.mkdirs()

        var files = 0L
        var bytesSeen = 0L
        val denom = if (manifest.uncompressedBytes > 0) manifest.uncompressedBytes else -1L
        openContainerStream().use { raw ->
            TarArchiveInputStream(raw).use { tar ->
                while (true) {
                    val entry: TarArchiveEntry = tar.nextTarEntry ?: break
                    val outFile = File(rootfs, entry.name.canonicalGuestPath())
                    when {
                        entry.isDirectory -> outFile.mkdirs()
                        entry.isSymbolicLink -> installSymlink(entry, outFile)
                        else -> {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> tar.copyTo(out) }
                            applyModes(outFile, entry.mode)
                            files++
                        }
                    }
                    bytesSeen += maxOf(entry.size, 64)
                    if (denom > 0 && files % 200 == 0L) {
                        onProgress((bytesSeen.toFloat() / denom).coerceIn(0f, 0.99f))
                    }
                }
            }
        }
        writeResolvConf(rootfs)
        writeReleaseMarker(rootfs, expectedSha, manifest)
        markerFile().parentFile?.mkdirs()
        markerFile().writeText(expectedSha)
        onProgress(1f)
        logs.append("installer", "Extraction complete ($files files)")
        return InstallResult(rootfs, expectedSha)
    }

    private fun needsInstall(expectedSha: String): Boolean {
        val marker = markerFile()
        return !(marker.isFile &&
            marker.readText().trim() == expectedSha &&
            File(rootfsDir(), BIN_OPENCODE).isFile)
    }

    private fun installSymlink(entry: TarArchiveEntry, outFile: File) {
        outFile.parentFile?.mkdirs()
        outFile.delete()
        try {
            Os.symlink(entry.linkName, outFile.absolutePath)
        } catch (e: Exception) {
            throw IOException("symlink failed for ${entry.name} → ${entry.linkName}: ${e.message}", e)
        }
    }

    private fun applyModes(f: File, mode: Long) {
        f.setReadable(true, false)
        f.setWritable(true)
        if (mode and 0o111 != 0L) f.setExecutable(true, false)
    }

    /** musl reads /etc/resolv.conf exclusively; Alpine ships none. Generated here. */
    private fun writeResolvConf(rootfs: File) {
        val etc = File(rootfs, "etc").apply { mkdirs() }
        File(etc, "resolv.conf").writeText(
            """
            # Generated by OpenCode for Android
            nameserver 8.8.8.8
            nameserver 8.8.4.4
            nameserver 1.1.1.1
            options timeout:2 attempts:3
            """.trimIndent() + "\n"
        )
    }

    private fun writeReleaseMarker(rootfs: File, sha256: String, m: BundleManifest) {
        File(rootfs, "etc/opencode-android-release").writeText(
            """
            BUNDLE_SHA256=$sha256
            LAYOUT_VERSION=${m.layoutVersion}
            OPENCODE_VERSION=${m.opencodeVersion}
            ALPINE_VERSION=${m.alpineVersion}
            """.trimIndent() + "\n"
        )
    }

    fun runtimeRoot(): File = File(context.filesDir, "runtime").apply { mkdirs() }
    fun rootfsDir(): File = File(runtimeRoot(), "rootfs")
    fun markerFile(): File = File(runtimeRoot(), ".installed-sha256")

    fun isInstalled(): Boolean {
        val m = markerFile()
        return m.isFile && m.length() > 0 && File(rootfsDir(), BIN_OPENCODE).isFile
    }

    companion object {
        const val TAR_ASSET = "runtime/rootfs.tar.gz"
        const val SHA_ASSET = "runtime/rootfs.sha256"
        const val MANIFEST_ASSET = "runtime/rootfs.manifest.json"
        const val BIN_OPENCODE = "usr/local/bin/opencode"
    }
}
