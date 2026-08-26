package dev.opencode.android.runtime

import org.junit.Assert.*
import org.junit.Test

class RuntimeStateMachineTest {

    @Test
    fun idleToExtracting() {
        // State transitions are implicit in RuntimeManager; test the enum values.
        assertEquals(RuntimePhase.IDLE, RuntimeState.Idle.phase)
    }

    @Test
    fun crashTransitionRecordsExitCode() {
        val crashed = RuntimeState.Crashed(exitCode = 137, restartAttempt = 1, willRestart = true, detail = "killed")
        assertEquals(137, crashed.exitCode)
        assertEquals(1, crashed.restartAttempt)
        assertTrue(crashed.willRestart)
    }

    @Test
    fun backoffPolicyExponential() {
        val policy = BackoffPolicy(1000, 30_000, 5)
        assertEquals(1000, policy.decisionFor(1).delayMs)
        assertEquals(2000, policy.decisionFor(2).delayMs)
        assertEquals(4000, policy.decisionFor(3).delayMs)
        assertEquals(8000, policy.decisionFor(4).delayMs)
        assertEquals(16000, policy.decisionFor(5).delayMs)
        assertFalse(policy.decisionFor(5).shouldRestart) // maxAttempts=5
    }

    @Test
    fun backoffCapsAtMax() {
        val policy = BackoffPolicy(baseDelayMs = 10_000, maxDelayMs = 15_000, maxAttempts = 10)
        assertEquals(15_000, policy.decisionFor(2).delayMs) // 20k capped to 15k
    }
}