package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.model.error.GeckoException
import com.gecko.domain.error.copyForUser
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.domain.repository.SecureKeyRepository
import com.gecko.domain.repository.UserPreferencesRepository
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import com.gecko.domain.usecase.TestProviderConnectionUseCase
import com.gecko.feature.settings.navigation.ProviderDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class ProviderDetailUiState(
    val id: String,
    val config: ProviderConfig? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isSavingKey: Boolean = false,
    val apiKeyValue: String? = null,
    val isApiKeyLoaded: Boolean = false,
    val saveKeyErrorMessage: String? = null,
    /** The app-wide model, shown here only when this is the key it belongs to. */
    val selectedModelId: String? = null,
    val isLoading: Boolean = true,
) {
    val providerId: ProviderId? get() = config?.providerId
    val label: String get() = config?.label.orEmpty()
    val enabled: Boolean get() = config?.enabled ?: false
    val hasApiKey: Boolean get() = config?.hasApiKey ?: false
    val connectionStatus: ConnectionStatus get() = config?.connectionStatus ?: ConnectionStatus.Untested
    val baseUrlOverride: String? get() = config?.baseUrlOverride
}

private data class KeyEditingState(
    val value: String?,
    val loaded: Boolean,
    val error: String?,
    val prefs: UserPreferences,
)

@HiltViewModel
class ProviderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerConfigRepository: ProviderConfigRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val saveProviderApiKeyUseCase: SaveProviderApiKeyUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val testProviderConnectionUseCase: TestProviderConnectionUseCase,
    private val refreshProviderModelsUseCase: RefreshProviderModelsUseCase,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>("configId")
        ?: savedStateHandle.toRoute<ProviderDetailRoute>().configId

    private val isLoadingModels = MutableStateFlow(false)
    private val isSavingKey = MutableStateFlow(false)

    // The stored key is fetched once up front (and refreshed in-place on save/clear) rather than
    // exposed as a reactive Flow — SecureKeyStore is a one-shot suspend read, not observable.
    private val apiKeyValue = MutableStateFlow<String?>(null)
    private val isApiKeyLoaded = MutableStateFlow(false)
    private val saveKeyErrorMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            apiKeyValue.value = secureKeyRepository.getApiKey(id)
            isApiKeyLoaded.value = true
        }
    }

    val uiState: StateFlow<ProviderDetailUiState> = combine(
        providerConfigRepository.observe(id),
        providerConfigRepository.observeModels(id),
        isLoadingModels,
        isSavingKey,
        // Grouped because combine only has typed overloads up to five flows, and the untyped
        // vararg version loses every type in the lambda.
        combine(apiKeyValue, isApiKeyLoaded, saveKeyErrorMessage, userPreferencesRepository.userPreferences, ::KeyEditingState),
    ) { config, models, loadingModels, savingKey, keyState ->
        ProviderDetailUiState(
            id = id,
            config = config,
            availableModels = models,
            isLoadingModels = loadingModels,
            isSavingKey = savingKey,
            apiKeyValue = keyState.value,
            isApiKeyLoaded = keyState.loaded,
            saveKeyErrorMessage = keyState.error,
            // Reads the preference chat actually uses, rather than the per-config column that
            // nothing outside this screen ever looked at.
            selectedModelId = keyState.prefs.defaultModelId
                .takeIf { keyState.prefs.defaultProviderConfigId == id },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderDetailUiState(id = id))

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { providerConfigRepository.setEnabled(id, enabled) }
    }

    fun setLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { providerConfigRepository.setLabel(id, trimmed) }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || isSavingKey.value) return
        isSavingKey.value = true
        saveKeyErrorMessage.value = null
        viewModelScope.launch {
            try {
                saveProviderApiKeyUseCase(id, trimmed)
                apiKeyValue.value = trimmed
                testProviderConnectionUseCase(id)
                refreshProviderModelsUseCase(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                saveKeyErrorMessage.value = "Couldn't save or check this key. Please try again."
            } finally {
                isSavingKey.value = false
            }
        }
    }

    fun clearApiKey() {
        if (isSavingKey.value) return
        viewModelScope.launch {
            secureKeyRepository.clearApiKey(id)
            providerConfigRepository.setConnectionStatus(id, ConnectionStatus.Untested)
            apiKeyValue.value = null
            saveKeyErrorMessage.value = null
        }
    }

    fun testConnection() {
        if (isSavingKey.value || uiState.value.connectionStatus == ConnectionStatus.Testing) return
        viewModelScope.launch { testProviderConnectionUseCase(id) }
    }

    fun refreshModels() {
        if (isLoadingModels.value) return
        isLoadingModels.value = true
        saveKeyErrorMessage.value = null
        viewModelScope.launch {
            try {
                refreshProviderModelsUseCase(id).onFailure { error ->
                    saveKeyErrorMessage.value = (error as? GeckoException)?.error?.copyForUser()?.explanation
                        ?: "Couldn't load models. Please try again."
                }
            } finally {
                isLoadingModels.value = false
            }
        }
    }

    fun setBaseUrlOverride(url: String?) {
        if (isSavingKey.value) return
        viewModelScope.launch {
            providerConfigRepository.setBaseUrlOverride(id, url?.trim()?.trimEnd('/')?.ifBlank { null })
            providerConfigRepository.saveModels(id, emptyList())
            providerConfigRepository.setConnectionStatus(id, ConnectionStatus.Untested)
            refreshModels()
        }
    }

    fun deleteProvider(onDeleted: () -> Unit) {
        viewModelScope.launch {
            providerConfigRepository.removeProvider(id)
            onDeleted()
        }
    }
}
