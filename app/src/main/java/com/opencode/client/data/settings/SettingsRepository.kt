package com.opencode.client.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opencode.client.core.appJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "opencode_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ServerKind { CLOUD, SELF_HOSTED, DEMO }

/** A configured OpenCode connection target. Secrets live exclusively in [com.opencode.client.core.secure.CredentialStore]. */
@Serializable
data class ServerProfile(
    val id: String,
    val label: String,
    val url: String,
    val username: String = "opencode",
    val isDemo: Boolean = false,
    /** Connection kind; drives UX (cloud = zero-setup, self-hosted = developer flow). */
    val kind: ServerKind = if (isDemo) ServerKind.DEMO else ServerKind.SELF_HOSTED
)

data class AppSettings(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val showReasoning: Boolean = true,
    val autoScrollEnabled: Boolean = true,
    val lastProjectPath: String? = null,
    val lastSessionId: String? = null,
    val onboarded: Boolean = false,
    val insecureHttpAcknowledged: Boolean = false,
    // ---- zero-setup additions ----
    val gatewayEmail: String? = null,
    val gatewayUrlOverride: String? = null,
    val developerMode: Boolean = false,
    val keepAliveServiceEnabled: Boolean = true
)

/**
 * Persistent app settings via Jetpack DataStore. Server credentials are NOT stored here -
 * only non-sensitive configuration. See CredentialStore for secrets.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVERS = stringPreferencesKey("servers")
        val ACTIVE_SERVER = stringPreferencesKey("active_server")
        val THEME = stringPreferencesKey("theme")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val SHOW_REASONING = booleanPreferencesKey("show_reasoning")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val LAST_PROJECT = stringPreferencesKey("last_project")
        val LAST_SESSION = stringPreferencesKey("last_session")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val INSECURE_ACK = booleanPreferencesKey("insecure_ack")
        val GATEWAY_EMAIL = stringPreferencesKey("gateway_email")
        val GATEWAY_OVERRIDE = stringPreferencesKey("gateway_url_override")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEEPALIVE_SERVICE = booleanPreferencesKey("keepalive_service")
    }

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private var started = false

    fun start() {
        if (started) return
        started = true
        CoroutineScope(Dispatchers.IO).launch {
            context.settingsDataStore.data.collect { prefs ->
                _settings.value = decode(prefs)
            }
        }
    }

    private fun decode(prefs: Preferences): AppSettings {
        val serversJson = prefs[Keys.SERVERS]
        val servers = try {
            serversJson?.let {
                appJson.decodeFromString(ListSerializer(ServerProfile.serializer()), it)
            }
        } catch (_: Exception) {
            null
        } ?: emptyList()

        return AppSettings(
            servers = servers,
            activeServerId = prefs[Keys.ACTIVE_SERVER],
            themeMode = prefs[Keys.THEME]?.let { t ->
                runCatching { ThemeMode.valueOf(t) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            showReasoning = prefs[Keys.SHOW_REASONING] ?: true,
            autoScrollEnabled = prefs[Keys.AUTO_SCROLL] ?: true,
            lastProjectPath = prefs[Keys.LAST_PROJECT],
            lastSessionId = prefs[Keys.LAST_SESSION],
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            insecureHttpAcknowledged = prefs[Keys.INSECURE_ACK] ?: false,
            gatewayEmail = prefs[Keys.GATEWAY_EMAIL],
            gatewayUrlOverride = prefs[Keys.GATEWAY_OVERRIDE],
            developerMode = prefs[Keys.DEVELOPER_MODE] ?: false,
            keepAliveServiceEnabled = prefs[Keys.KEEPALIVE_SERVICE] ?: true
        )
    }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences) {
        context.settingsDataStore.edit(transform)
    }

    suspend fun addOrUpdateServer(server: ServerProfile) {
        edit { prefs ->
            val current = try {
                prefs[Keys.SERVERS]?.let {
                    appJson.decodeFromString(ListSerializer(ServerProfile.serializer()), it)
                }
            } catch (_: Exception) {
                null
            } ?: emptyList()
            val updated = current.filterNot { it.id == server.id } + server
            prefs.toMutablePreferences().apply {
                set(Keys.SERVERS, appJson.encodeToString(ListSerializer(ServerProfile.serializer()), updated))
            }
        }
    }

    suspend fun removeServer(id: String) {
        edit { prefs ->
            val current = try {
                prefs[Keys.SERVERS]?.let {
                    appJson.decodeFromString(ListSerializer(ServerProfile.serializer()), it)
                }
            } catch (_: Exception) {
                null
            } ?: emptyList()
            val updated = current.filterNot { it.id == id }
            prefs.toMutablePreferences().apply {
                set(Keys.SERVERS, appJson.encodeToString(ListSerializer(ServerProfile.serializer()), updated))
                if (prefs[Keys.ACTIVE_SERVER] == id) remove(Keys.ACTIVE_SERVER)
            }
        }
    }

    suspend fun setActiveServer(id: String) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.ACTIVE_SERVER, id)
    }

    suspend fun setActiveServerAndPersist(server: ServerProfile) {
        addOrUpdateServer(server)
        setActiveServer(server.id)
    }

    suspend fun setTheme(mode: ThemeMode) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.THEME, mode.name)
    }

    suspend fun setNotifications(enabled: Boolean) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.NOTIFICATIONS, enabled)
    }

    suspend fun setShowReasoning(enabled: Boolean) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.SHOW_REASONING, enabled)
    }

    suspend fun setAutoScroll(enabled: Boolean) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.AUTO_SCROLL, enabled)
    }

    suspend fun setLastLocation(projectPath: String?, sessionId: String?) = edit { prefs ->
        prefs.toMutablePreferences().apply {
            if (projectPath != null) set(Keys.LAST_PROJECT, projectPath) else remove(Keys.LAST_PROJECT)
            if (sessionId != null) set(Keys.LAST_SESSION, sessionId) else remove(Keys.LAST_SESSION)
        }
    }

    suspend fun setOnboarded() = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.ONBOARDED, true)
    }

    suspend fun acknowledgeInsecureHttp() = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.INSECURE_ACK, true)
    }

    // ---- zero-setup additions -------------------------------------------------

    suspend fun setGatewaySession(email: String?) = edit { prefs ->
        prefs.toMutablePreferences().apply {
            if (email != null) set(Keys.GATEWAY_EMAIL, email) else remove(Keys.GATEWAY_EMAIL)
        }
    }

    suspend fun setGatewayUrlOverride(url: String?) = edit { prefs ->
        prefs.toMutablePreferences().apply {
            if (url.isNullOrBlank()) remove(Keys.GATEWAY_OVERRIDE) else set(Keys.GATEWAY_OVERRIDE, url.trim())
        }
    }

    suspend fun setDeveloperMode(enabled: Boolean) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.DEVELOPER_MODE, enabled)
    }

    suspend fun setKeepAliveService(enabled: Boolean) = edit { prefs ->
        prefs.toMutablePreferences().set(Keys.KEEPALIVE_SERVICE, enabled)
    }

    fun serverById(id: String?): ServerProfile? =
        _settings.value.servers.firstOrNull { it.id == id }

    companion object {
        fun newServerId(): String = UUID.randomUUID().toString()
    }
}
