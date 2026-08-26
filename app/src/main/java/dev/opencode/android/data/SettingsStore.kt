package dev.opencode.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val defaultProvider: String = "",
    val defaultModel: String = "",
    val defaultAgent: String = "build",
    val allowBashAutoApprove: Boolean = false,
    val allowEditAutoApprove: Boolean = false,
    val runtimeDebugLogs: Boolean = false,
    val onboardingComplete: Boolean = false,
) {
    /** OPENCODE_CONFIG_CONTENT JSON injected into the guest runtime. */
    fun toConfigContentJson(defaultModelResolved: String?): String {
        val sb = StringBuilder("{")
        sb.append("\"share\":\"disabled\",\"autoupdate\":false")
        defaultModelResolved?.takeIf { it.isNotBlank() }?.let {
            sb.append(",\"model\":\"").append(it.replace("\"", "\\\"")).append("\"")
        }
        sb.append(",\"permission\":{")
        sb.append("\"edit\":\"").append(if (allowEditAutoApprove) "allow" else "ask").append("\"")
        sb.append(",\"bash\":\"").append(if (allowBashAutoApprove) "allow" else "ask").append("\"")
        sb.append("}")
        sb.append("}")
        return sb.toString()
    }
}

class SettingsStore(private val context: Context) {
    private object K {
        val provider = stringPreferencesKey("default_provider")
        val model = stringPreferencesKey("default_model")
        val agent = stringPreferencesKey("default_agent")
        val bashAuto = booleanPreferencesKey("bash_auto_approve")
        val editAuto = booleanPreferencesKey("edit_auto_approve")
        val debugLogs = booleanPreferencesKey("runtime_debug_logs")
        val onboarded = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultProvider = p[K.provider] ?: "",
            defaultModel = p[K.model] ?: "",
            defaultAgent = p[K.agent] ?: "build",
            allowBashAutoApprove = p[K.bashAuto] ?: false,
            allowEditAutoApprove = p[K.editAuto] ?: false,
            runtimeDebugLogs = p[K.debugLogs] ?: false,
            onboardingComplete = p[K.onboarded] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingComplete() = context.dataStore.edit { it[K.onboarded] = true }
    suspend fun setDefaultModel(provider: String, model: String) = context.dataStore.edit {
        it[K.provider] = provider
        it[K.model] = model
    }
    suspend fun setDefaultAgent(agent: String) = context.dataStore.edit { it[K.agent] = agent }
    suspend fun setBashAutoApprove(v: Boolean) = context.dataStore.edit { it[K.bashAuto] = v }
    suspend fun setEditAutoApprove(v: Boolean) = context.dataStore.edit { it[K.editAuto] = v }
    suspend fun setRuntimeDebugLogs(v: Boolean) = context.dataStore.edit { it[K.debugLogs] = v }
}
