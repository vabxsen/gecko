package com.gecko.feature.settings.modelprefs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import com.gecko.feature.settings.navigation.DefaultModelSelectionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DefaultModelSelectionUiState(
    val providerId: ProviderId? = null,
    val models: List<ModelInfo> = emptyList(),
    val selectedModelId: String? = null,
)

@HiltViewModel
class DefaultModelSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val configId: String = savedStateHandle.toRoute<DefaultModelSelectionRoute>().configId

    val uiState: StateFlow<DefaultModelSelectionUiState> = combine(
        providerConfigRepository.observe(configId),
        providerConfigRepository.observeModels(configId),
        userPreferencesRepository.userPreferences,
    ) { config, models, prefs ->
        DefaultModelSelectionUiState(
            providerId = config?.providerId,
            models = models,
            // Only show a checkmark if this provider is already the app-wide default — visiting
            // another provider's model list shouldn't show a stale selection from a different key.
            selectedModelId = prefs.defaultModelId.takeIf { prefs.defaultProviderConfigId == configId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultModelSelectionUiState())

    /** Picking a model here makes this provider the default too — one step instead of two. */
    fun selectModel(modelId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultProviderConfig(configId)
            userPreferencesRepository.setDefaultModel(modelId)
        }
    }
}
