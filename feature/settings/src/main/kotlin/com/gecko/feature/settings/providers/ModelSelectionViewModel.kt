package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
import com.gecko.feature.settings.navigation.ModelSelectionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelSelectionUiState(
    val providerLabel: String = "",
    val providerId: ProviderId? = null,
    val baseUrlOverride: String? = null,
    val models: List<ModelInfo> = emptyList(),
    val selectedModelId: String? = null,
    val isLoading: Boolean = false,
    val error: GeckoError? = null,
)

/**
 * The one model picker in Settings.
 *
 * There used to be two, on separate screens reached from separate menu entries, and only one of
 * them worked: the AI Providers one wrote `ProviderConfig.selectedModelId`, which the chat screen
 * never read. Someone who picked a model there watched nothing change, with no way to tell why.
 * This writes the preference chat actually uses.
 */
@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val refreshProviderModelsUseCase: RefreshProviderModelsUseCase,
) : ViewModel() {

    /**
     * Type-safe navigation stores a route's properties in the handle under their own names, so the
     * direct read is what runs in practice. [toRoute] stays as the fallback because it's the
     * contract navigation actually guarantees — and it needs a real `Bundle`, which a JVM unit test
     * doesn't have, so without the direct read this ViewModel would be untestable.
     */
    private val configId: String = savedStateHandle.get<String>(CONFIG_ID_ARG)
        ?: savedStateHandle.toRoute<ModelSelectionRoute>().configId
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<GeckoError?>(null)

    val uiState: StateFlow<ModelSelectionUiState> = combine(
        providerConfigRepository.observe(configId),
        providerConfigRepository.observeModels(configId),
        userPreferencesRepository.userPreferences,
        isLoading,
        error,
    ) { config, models, prefs, loading, currentError ->
        ModelSelectionUiState(
            providerLabel = config?.label?.ifBlank { config.providerId.displayName }.orEmpty(),
            providerId = config?.providerId,
            baseUrlOverride = config?.baseUrlOverride,
            models = models,
            // Only tick a model if this key is the one in use — another key's list shouldn't show
            // a checkmark borrowed from somewhere else.
            selectedModelId = prefs.defaultModelId.takeIf { prefs.defaultProviderConfigId == configId },
            isLoading = loading,
            error = currentError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelSelectionUiState())

    init {
        // A key whose catalog was never cached — its fetch failed when the key was saved, or the
        // device was offline then — used to land here on an empty list telling the user to go and
        // tap Refresh on a screen they'd have to find themselves. Just fetch it.
        viewModelScope.launch {
            if (providerConfigRepository.observeModels(configId).first().isEmpty()) refreshModels()
        }
    }

    /** Picking a model also makes its key the one in use — one decision, not two. */
    fun selectModel(modelId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultProviderConfig(configId)
            userPreferencesRepository.setDefaultModel(modelId)
        }
    }

    fun refreshModels() {
        if (isLoading.value) return
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            refreshProviderModelsUseCase(configId).onFailure {
                error.value = (it as? GeckoException)?.error
                    ?: GeckoError(ErrorKind.Unknown, technicalDetail = it.message)
            }
            isLoading.value = false
        }
    }

    fun dismissError() {
        error.value = null
    }

    private companion object {
        const val CONFIG_ID_ARG = "configId"
    }
}
