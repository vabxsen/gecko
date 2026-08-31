package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.feature.settings.navigation.ProviderModelSelectionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderModelSelectionUiState(
    val providerId: ProviderId? = null,
    val models: List<ModelInfo> = emptyList(),
    val selectedModelId: String? = null,
)

@HiltViewModel
class ProviderModelSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerConfigRepository: ProviderConfigRepository,
) : ViewModel() {

    private val configId: String = savedStateHandle.toRoute<ProviderModelSelectionRoute>().configId

    val uiState: StateFlow<ProviderModelSelectionUiState> = combine(
        providerConfigRepository.observe(configId),
        providerConfigRepository.observeModels(configId),
    ) { config, models ->
        ProviderModelSelectionUiState(
            providerId = config?.providerId,
            models = models,
            selectedModelId = config?.selectedModelId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderModelSelectionUiState())

    fun selectModel(modelId: String) {
        viewModelScope.launch { providerConfigRepository.setSelectedModel(configId, modelId) }
    }
}
