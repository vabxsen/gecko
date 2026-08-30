package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.SecureKeyRepository
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

data class ProviderDetailUiState(
    val providerId: ProviderId,
    val config: ProviderConfig? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isSavingKey: Boolean = false,
) {
    val enabled: Boolean get() = config?.enabled ?: false
    val hasApiKey: Boolean get() = config?.hasApiKey ?: false
    val connectionStatus: ConnectionStatus get() = config?.connectionStatus ?: ConnectionStatus.Untested
    val selectedModelId: String? get() = config?.selectedModelId
}

@HiltViewModel
class ProviderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerConfigRepository: ProviderConfigRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val saveProviderApiKeyUseCase: SaveProviderApiKeyUseCase,
    private val testProviderConnectionUseCase: TestProviderConnectionUseCase,
    private val refreshProviderModelsUseCase: RefreshProviderModelsUseCase,
) : ViewModel() {

    private val providerId: ProviderId = ProviderId.fromSlug(savedStateHandle.toRoute<ProviderDetailRoute>().providerSlug)
        ?: ProviderId.OPENAI

    private val isLoadingModels = MutableStateFlow(false)
    private val isSavingKey = MutableStateFlow(false)

    val uiState: StateFlow<ProviderDetailUiState> = combine(
        providerConfigRepository.observe(providerId),
        providerConfigRepository.observeModels(providerId),
        isLoadingModels,
        isSavingKey,
    ) { config, models, loadingModels, savingKey ->
        ProviderDetailUiState(
            providerId = providerId,
            config = config,
            availableModels = models,
            isLoadingModels = loadingModels,
            isSavingKey = savingKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderDetailUiState(providerId = providerId))

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { providerConfigRepository.setEnabled(providerId, enabled) }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            isSavingKey.value = true
            saveProviderApiKeyUseCase(providerId, trimmed)
            isSavingKey.value = false
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            secureKeyRepository.clearApiKey(providerId)
            providerConfigRepository.setConnectionStatus(providerId, ConnectionStatus.Untested)
        }
    }

    fun testConnection() {
        viewModelScope.launch { testProviderConnectionUseCase(providerId) }
    }

    fun refreshModels() {
        viewModelScope.launch {
            isLoadingModels.value = true
            refreshProviderModelsUseCase(providerId)
            isLoadingModels.value = false
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch { providerConfigRepository.setSelectedModel(providerId, modelId) }
    }

    fun setBaseUrlOverride(url: String?) {
        viewModelScope.launch { providerConfigRepository.setBaseUrlOverride(providerId, url?.trim()?.ifBlank { null }) }
    }
}
