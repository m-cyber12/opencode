package dev.opencode.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.opencode.android.AppContainer
import dev.opencode.android.data.Settings
import dev.opencode.android.data.SettingsStore
import dev.opencode.android.data.SecureCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settingsStore.settings
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, Settings())

    val credentials = MutableStateFlow(container.secureCredentials.snapshot())

    val providers = listOf(
        Provider("anthropic", "Anthropic", "sk-ant-"),
        Provider("openai", "OpenAI", "sk-"),
        Provider("google", "Google AI", "AIza"),
        Provider("cohere", "Cohere", ""),
        Provider("azure", "Azure OpenAI", ""),
        Provider("custom", "Custom (OpenAI-compatible)", ""),
    )

    fun updateProviderKey(providerId: String, key: String) {
        if (key.isBlank()) {
            container.secureCredentials.remove(providerId)
        } else {
            container.secureCredentials.setApiKey(providerId, key)
        }
        credentials.value = container.secureCredentials.snapshot()
    }

    fun getKey(providerId: String): String = credentials.value[providerId] ?: ""

    data class Provider(val id: String, val name: String, val prefix: String)

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
    }
}