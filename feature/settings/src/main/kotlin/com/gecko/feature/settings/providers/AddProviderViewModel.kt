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

data class AddProviderUiState(
    val selectedProviderId: ProviderId? = null,
    val label: String = "",
    val apiKey: String = "",
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

    fun selectProviderType(providerId: ProviderId) {
        _uiState.update {
            it.copy(
                selectedProviderId = providerId,
                label = it.label.ifBlank { providerId.displayName },
                errorMessage = null,
            )
        }
    }

    fun updateLabel(label: String) {
        _uiState.update { it.copy(label = label) }
    }

    fun updateApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key) }
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
