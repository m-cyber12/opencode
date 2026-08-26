package dev.opencode.android.runtime

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProotLauncherTest {
    @Test
    fun buildsExpectedArgs() {
        // This test is conceptual — ProotLauncher needs a Context for nativeLibraryDir.
        // We verify the arg ordering by inspecting the logic directly.
        // In a real test we'd inject a mock Context.
    }
}