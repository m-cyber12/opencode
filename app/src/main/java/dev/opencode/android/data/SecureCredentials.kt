package dev.opencode.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Provider credentials sealed with an Android Keystore master key
 * (AES256-GCM via androidx.security-crypto).
 *
 * Secrets are NEVER written to plaintext storage and NEVER placed in the APK.
 * They reach the OpenCode runtime only as process environment content at spawn
 * time (OPENCODE_AUTH_CONTENT) inside the app's own sandboxed process tree.
 */
class SecureCredentials(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "opencode_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun setApiKey(providerId: String, key: String) {
        prefs.edit().putString("key:$providerId", key).apply()
    }

    fun getApiKey(providerId: String): String? =
        prefs.getString("key:$providerId", null)?.takeIf { it.isNotBlank() }

    fun remove(providerId: String) {
        prefs.edit().remove("key:$providerId").apply()
    }

    /** All configured providers → keys (used to build OPENCODE_AUTH_CONTENT). */
    fun snapshot(): Map<String, String> =
        prefs.all.keys.filter { it.startsWith("key:") }.associate {
            it.removePrefix("key:") to prefs.getString(it, "").orEmpty()
        }.filterValues { it.isNotBlank() }

    /**
     * auth.json-compatible payload consumed by OpenCode via OPENCODE_AUTH_CONTENT.
     * Schema: {"providerId": {"type":"api","key":"…"}}
     */
    fun buildAuthContentJson(): String? {
        val snap = snapshot()
        if (snap.isEmpty()) return null
        val entries = snap.entries.joinToString(",") { (p, k) ->
            "\"${jsonEscape(p)}\":{\"type\":\"api\",\"key\":\"${jsonEscape(k)}\"}"
        }
        return "{$entries}"
    }

    companion object {
        fun jsonEscape(s: String): String = buildString {
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}
