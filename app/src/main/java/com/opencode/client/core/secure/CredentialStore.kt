package com.opencode.client.core.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores server credentials using Android Keystore-backed encrypted preferences.
 *
 * Security contract:
 * - Secrets never leave this class except to the AuthInterceptor.
 * - Never logged, never included in backups (excluded via backup rules), never returned in bulk.
 * - If Keystore-backed storage cannot be initialised (broken vendor keystore), construction fails
 *   and the app shows a security error instead of silently degrading to plaintext.
 */
interface CredentialStore {
    fun put(serverId: String, secret: String)
    fun get(serverId: String): String?
    fun remove(serverId: String)
    fun clearAll()
}

class EncryptedCredentialStore(context: Context) : CredentialStore {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "opencode_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun key(serverId: String) = "secret:$serverId"

    override fun put(serverId: String, secret: String) {
        prefs.edit().putString(key(serverId), secret).apply()
    }

    override fun get(serverId: String): String? = prefs.getString(key(serverId), null)

    override fun remove(serverId: String) {
        prefs.edit().remove(key(serverId)).apply()
    }

    override fun clearAll() {
        val keys = prefs.all.keys.filter { it.startsWith("secret:") }
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }
}
