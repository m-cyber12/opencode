package com.opencode.client.core.native

import android.content.Context
import java.io.File

/**
 * Locates the native runtime components Android extracted from jniLibs into
 * [ApplicationInfo.nativeLibraryDir].
 *
 * EMPIRICAL: whether execve on these files actually works is proven by gate G1, not by this
 * class. Its only job is honest path resolution + presence checks.
 */
object NativeArtifacts {

    data class Artifacts(
        val proot: File,
        val prootLoader: File
    )

    sealed interface Status {
        data class Ready(val artifacts: Artifacts) : Status
        data class Missing(val missing: List<String>) : Status
    }

    fun resolve(context: Context): Status {
        val dir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(dir, "libopx-proot.so")
        val loader = File(dir, "libopx-proot-loader.so")
        val missing = buildList {
            if (!proot.exists()) add("libopx-proot.so")
            if (!loader.exists()) add("libopx-proot-loader.so")
        }
        return if (missing.isEmpty()) Status.Ready(Artifacts(proot, loader)) else Status.Missing(missing)
    }

    fun abiSupported(): Boolean =
        android.os.Build.SUPPORTED_ABIS.any { it.startsWith("arm64") }
}
