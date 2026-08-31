package com.gecko.feature.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One selectable row in "Add API key" — either a plain protocol (baseUrl = null) or an
 * OpenAI-compatible service reached through the OpenAI protocol with a different base URL. */
data class AddProviderOption(val label: String, val providerId: ProviderId, val baseUrl: String?)

val ADD_PROVIDER_OPTIONS: List<AddProviderOption> =
    ProviderId.entries.map { AddProviderOption(it.displayName, it, null) } +
        OPENAI_COMPATIBLE_ENDPOINTS.filter { it.baseUrl != null }
            .map { AddProviderOption(it.label, ProviderId.OPENAI, it.baseUrl) }

data class AddProviderUiState(
    val selectedProviderId: ProviderId? = null,
    val label: String = "",
    val labelManuallyEdited: Boolean = false,
    val apiKey: String = "",
    val baseUrlOverride: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = selectedProviderId != null && apiKey.isNotBlank() && !isSaving
}

@HiltViewModel
class AddProviderViewModel @Inject constructor(
    private val providerConfigRepository: ProviderConfigRepository,
    private val saveProviderApiKeyUseCase: SaveProviderApiKeyUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProviderUiState())
    val uiState: StateFlow<AddProviderUiState> = _uiState.asStateFlow()

    fun selectOption(option: AddProviderOption) {
        _uiState.update {
            it.copy(
                selectedProviderId = option.providerId,
                baseUrlOverride = option.baseUrl.orEmpty(),
                label = if (it.labelManuallyEdited) it.label else option.label,
                errorMessage = null,
            )
        }
    }

    fun updateLabel(label: String) {
        _uiState.update { it.copy(label = label, labelManuallyEdited = true) }
    }

    fun updateApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    fun updateBaseUrlOverride(url: String) {
        _uiState.update { it.copy(baseUrlOverride = url) }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val providerId = state.selectedProviderId ?: return
        val key = state.apiKey.trim()
        if (key.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val label = state.label.trim().ifBlank { providerId.displayName }
            providerConfigRepository.addProvider(providerId, label)
                .onSuccess { id ->
                    if (providerId == ProviderId.OPENAI) {
                        providerConfigRepository.setBaseUrlOverride(id, state.baseUrlOverride.trim().ifBlank { null })
                    }
                    saveProviderApiKeyUseCase(id, key)
                    _uiState.update { it.copy(isSaving = false) }
                    onSaved()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Couldn't save this key") }
                }
        }
    }
}
