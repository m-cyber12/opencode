package com.opencode.client.runtime

import android.content.Context
import com.opencode.client.core.native.NativeArtifacts
import java.io.File
import java.security.MessageDigest

/**
 * First-run (and version-change) materialization of the embedded Linux rootfs into app-private
 * storage. Idempotent: a SHA-256 marker of the bundled archive decides SKIP vs RE-EXTRACT.
 *
 * EMPIRICAL: whether the extracted guest actually boots is gate G1's job.
 */
class RootfsBootstrap(
    private val context: Context,
    private val progress: ((stage: Stage, fraction: Float) -> Unit)? = null
) {

    enum class Stage { VERIFYING, EXTRACTING, CONFIGURING, DONE }

    data class Layout(
        val rootfsDir: File,
        val homeDir: File,
        val projectDir: File,
        val logDir: File,
        val markerFile: File
    )

    fun layout(): Layout {
        val base = File(context.filesDir, "runtime")
        val rootfs = File(base, "rootfs")
        return Layout(
            rootfsDir = rootfs,
            homeDir = File(rootfs, "home/opencode"),
            projectDir = File(rootfs, "home/opencode/project"),
            logDir = File(base, "logs"),
            markerFile = File(base, "rootfs.marker.txt")
        )
    }

    /** @return true when extraction was performed, false when already up to date. */
    fun ensureExtracted(): Boolean {
        val assetManager = context.assets
        val assetName = "runtime/opencode-rootfs-arm64.tar.gz"
        val layout = layout()
        layout.logDir.mkdirs()

        progress?.invoke(Stage.VERIFYING, 0f)
        val digest = sha256Of(assetManager.open(assetName))
        if (layout.rootfsDir.exists() && readMarker() == digest) {
            ensureSkeleton(layout)
            progress?.invoke(Stage.DONE, 1f)
            return false
        }

        // Fresh extract into a temp dir then atomic-ish swap, so a crash never leaves half a rootfs.
        val staging = File(context.filesDir, "runtime/rootfs.staging")
        staging.deleteRecursively()
        staging.mkdirs()

        progress?.invoke(Stage.EXTRACTING, 0f)
        val total = assetManager.open(assetName).use { it.available().toLong().coerceAtLeast(1) }
        var lastFraction = -1f
        val extractor = TarExtractor { _, totalBytes ->
            val f = (totalBytes.toFloat() / total).coerceIn(0f, 0.95f)
            if (f - lastFraction > 0.02f) { lastFraction = f; progress?.invoke(Stage.EXTRACTING, f) }
        }
        assetManager.open(assetName).use { raw ->
            extractor.extractGzipTar(raw.buffered(), staging, total)
        }

        progress?.invoke(Stage.CONFIGURING, 0.97f)
        configureGuest(staging)
        layout.rootfsDir.deleteRecursively()
        if (!staging.renameTo(layout.rootfsDir)) {
            // rename across same volume should succeed; fall back to copy-move for odd filesystems
            staging.copyRecursively(layout.rootfsDir, overwrite = true)
            staging.deleteRecursively()
        }
        writeMarker(digest)
        ensureSkeleton(layout)
        progress?.invoke(Stage.DONE, 1f)
        return true
    }

    private fun ensureSkeleton(layout: Layout) {
        layout.homeDir.mkdirs()
        layout.projectDir.mkdirs()
    }

    /**
     * Post-extract guest configuration that must reflect the DEVICE (not the CI builder):
     * resolv.conf from Android's active networks when available, else static resolvers.
     * Everything else ships preconfigured inside the image.
     */
    private fun configureGuest(rootfs: File) {
        val resolv = File(rootfs, "etc/resolv.conf")
        runCatching {
            val lines = mutableListOf<String>()
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            for (network in cm.allNetworks) {
                val lp = cm.getLinkProperties(network) ?: continue
                for (dns in lp.dnsServers) lines += "nameserver ${dns.hostAddress}"
            }
            if (lines.isNotEmpty()) resolv.writeText(lines.distinct().take(4).joinToString("\n", postfix = "\n"))
        }
    }
            }
            if (lines.isNotEmpty()) resolv.writeText(lines.distinct().take(4).joinToString("\n", postfix = "\n"))
        }
    }

    private fun sha256Of(input: java.io.InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        input.use { s ->
            while (true) {
                val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readMarker(): String? =
        layout().markerFile.takeIf { it.exists() }?.readText()?.trim()

    private fun writeMarker(digest: String) {
        layout().markerFile.writeText(digest + "\n")
    }
}
