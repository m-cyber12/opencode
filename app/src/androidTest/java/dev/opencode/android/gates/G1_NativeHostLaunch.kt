package dev.opencode.android.gates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opencode.android.runtime.LogRingBuffer
import dev.opencode.android.runtime.RuntimeInstaller
import dev.opencode.android.runtime.RuntimeManager
import dev.opencode.android.runtime.RuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G1: Android native host can launch the execution layer.
 * Verifies RuntimeManager can spawn PRoot process (reaches STARTING phase).
 */
@RunWith(AndroidJUnit4::class)
class G1_NativeHostLaunch {
    @Test
    fun hostCanLaunchExecutionLayer() = runBlocking(Dispatchers.IO) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val logs = LogRingBuffer()
        val installer = RuntimeInstaller(ctx, logs)
        val manager = RuntimeManager(
            context = ctx,
            installer = installer,
            launcher = dev.opencode.android.runtime.ProotLauncher(ctx, installer, logs),
            logs = logs,
        )
        // Just ensure startLocked reaches STARTING (port alloc + process spawn attempt)
        // We inject a failing factory to stop before health wait.
        var reached = false
        try {
            manager.ensureStarted { _ ->
                reached = true
                throw AssertionError("stop here")
            }
        } catch (_: Throwable) {}
        assertTrue("RuntimeManager.startLocked should reach STARTING phase", reached)
    }
}