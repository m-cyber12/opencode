package dev.opencode.android.runtime

import kotlin.math.min
import kotlin.math.pow

/**
 * Exponential backoff with jitter-free determinism for testability.
 * delays: base * 2^(attempt-1), capped at maxDelayMs.
 */
class BackoffPolicy(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
    private val maxAttempts: Int = 5,
) {
    data class Decision(val shouldRestart: Boolean, val delayMs: Long, val attempt: Int)

    fun decisionFor(attempt: Int): Decision {
        if (attempt >= maxAttempts || attempt < 1) return Decision(false, 0L, attempt)
        val exp = (attempt - 1).coerceAtLeast(0)
        val raw = baseDelayMs.toDouble() * 2.0.pow(exp.toDouble())
        return Decision(true, min(raw.toLong(), maxDelayMs), attempt + 1)
    }
}
