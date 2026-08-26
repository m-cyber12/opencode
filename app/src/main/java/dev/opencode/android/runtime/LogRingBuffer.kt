package dev.opencode.android.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory ring buffer of recent runtime log lines.
 * Secrets (API keys / bearer tokens) are redacted before storage so the
 * diagnostics screen can be shown safely (spec §27/§30).
 */
class LogRingBuffer(private val capacity: Int = 1000) {
    private val lock = Any()
    private val lines = ArrayDeque<String>(capacity)
    private val _flow = MutableStateFlow<List<String>>(emptyList())
    val flow: StateFlow<List<String>> get() = _flow

    fun append(source: String, message: String) {
        val stamped = "%s [%s] %s".format(
            TIME_FMT(),
            source,
            redact(message),
        )
        synchronized(lock) {
            if (lines.size >= capacity) lines.removeFirst()
            lines.addLast(stamped)
            _flow.value = lines.toList()
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun clear() = synchronized(lock) { lines.clear(); _flow.value = emptyList() }

    companion object {
        private fun TIME_FMT(): String =
            java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())

        private val SECRET_PATTERNS = listOf(
            Regex("(sk-[A-Za-z0-9_-]{8,})"),
            Regex("(Bearer\\s+)[A-Za-z0-9._\\-/=+]{8,}", RegexOption.IGNORE_CASE),
            Regex("(api[_-]?key[\"'=:\\s]+)[A-Za-z0-9._\\-/=+]{8,}", RegexOption.IGNORE_CASE),
            Regex("(gh[pousr]_[A-Za-z0-9]{20,})"),
        )

        fun redact(message: String): String {
            var out = message
            for (r in SECRET_PATTERNS) {
                out = r.replace(out) { m ->
                    val keep = m.groupValues[1]
                    "$keep<redacted>"
                }
            }
            return out
        }
    }
}
