package com.gecko.feature.settings.modelprefs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelPreferencesUiState(
    val enabledProviders: List<ProviderConfig> = emptyList(),
    val defaultProviderConfigId: String? = null,
    val defaultModelId: String? = null,
    val modelsForDefaultProvider: List<ModelInfo> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ModelPreferencesViewModel @Inject constructor(
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val enabledProviders = providerConfigRepository.observeAll()
        .map { configs -> configs.filter { it.enabled && it.hasApiKey } }

    private val defaultSelection = userPreferencesRepository.userPreferences
        .map { it.defaultProviderConfigId to it.defaultModelId }

    private val modelsForDefaultProvider = defaultSelection.flatMapLatest { (configId, _) ->
        if (configId == null) flowOf(emptyList()) else providerConfigRepository.observeModels(configId)
    }

    val uiState: StateFlow<ModelPreferencesUiState> = combine(
        enabledProviders,
        defaultSelection,
        modelsForDefaultProvider,
    ) { providers, (defaultProviderConfigId, defaultModelId), models ->
        ModelPreferencesUiState(
            enabledProviders = providers,
            defaultProviderConfigId = defaultProviderConfigId,
            defaultModelId = defaultModelId,
            modelsForDefaultProvider = models,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelPreferencesUiState())

    fun selectDefaultProvider(configId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultProviderConfig(configId)
            userPreferencesRepository.setDefaultModel(null)
        }
    }

    fun selectDefaultModel(modelId: String) {
        viewModelScope.launch { userPreferencesRepository.setDefaultModel(modelId) }
    }
}
