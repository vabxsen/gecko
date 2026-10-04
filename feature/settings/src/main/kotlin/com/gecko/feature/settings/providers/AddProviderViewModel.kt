package com.gecko.feature.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.usecase.ConnectProviderUseCase
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddProviderOption(val label: String, val providerId: ProviderId, val baseUrl: String?)

val ADD_PROVIDER_OPTIONS: List<AddProviderOption> =
    ProviderId.entries.map { AddProviderOption(it.displayName, it, null) } +
        OPENAI_COMPATIBLE_ENDPOINTS.filter { it.baseUrl != null }
            .map { AddProviderOption(it.label, ProviderId.OPENAI, it.baseUrl) }

/** Local hints only. Generic sk- keys are ambiguous and must never be tried at multiple vendors. */
internal fun detectProvider(key: String): AddProviderOption? {
    val trimmed = key.trim()
    val label = when {
        trimmed.startsWith("sk-ant-api") -> "Anthropic"
        trimmed.startsWith("sk-or-v1-") -> "OpenRouter"
        trimmed.startsWith("nvapi-") -> "NVIDIA NIM"
        trimmed.startsWith("AIza") -> "Google Gemini"
        trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-svcacct-") -> "OpenAI"
        else -> return null
    }
    return ADD_PROVIDER_OPTIONS.first { it.label == label }
}

data class AddProviderUiState(
    val selectedProviderId: ProviderId? = null,
    val providerLabel: String = "",
    val providerManuallySelected: Boolean = false,
    val label: String = "",
    val apiKey: String = "",
    val baseUrlOverride: String = "",
    val isSaving: Boolean = false,
    val error: GeckoError? = null,
) {
    val canSave: Boolean get() = selectedProviderId != null && apiKey.isNotBlank() && !isSaving
}

@HiltViewModel
class AddProviderViewModel @Inject constructor(
    private val providerConfigRepository: ProviderConfigRepository,
    private val saveProviderApiKeyUseCase: SaveProviderApiKeyUseCase,
    private val connectProviderUseCase: ConnectProviderUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddProviderUiState())
    val uiState: StateFlow<AddProviderUiState> = _uiState.asStateFlow()
    private var connected = false

    fun selectOption(option: AddProviderOption) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(selectedProviderId = option.providerId, providerLabel = option.label,
                providerManuallySelected = true, baseUrlOverride = option.baseUrl.orEmpty(), error = null)
        }
    }

    fun updateLabel(label: String) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(label = label) }
    }

    fun updateApiKey(key: String) {
        if (_uiState.value.isSaving) return
        val detected = detectProvider(key)
        _uiState.update {
            if (it.providerManuallySelected) it.copy(apiKey = key, error = null)
            else it.copy(apiKey = key, selectedProviderId = detected?.providerId,
                providerLabel = detected?.label.orEmpty(), baseUrlOverride = detected?.baseUrl.orEmpty(), error = null)
        }
    }

    fun updateBaseUrlOverride(url: String) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(baseUrlOverride = url, providerManuallySelected = true, error = null) }
    }

    fun save(onSaved: (String) -> Unit) {
        val state = _uiState.value
        if (!state.canSave || connected) return
        val providerId = state.selectedProviderId ?: return
        val key = state.apiKey.trim()
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            var createdId: String? = null
            var ready = false
            try {
                val label = state.label.trim().ifBlank { state.providerLabel }
                val id = providerConfigRepository.addProvider(providerId, label).getOrThrow()
                createdId = id
                providerConfigRepository.setEnabled(id, false)
                if (providerId == ProviderId.OPENAI) {
                    providerConfigRepository.setBaseUrlOverride(id, state.baseUrlOverride.trim().ifBlank { null })
                }
                saveProviderApiKeyUseCase(id, key)
                connectProviderUseCase(id).getOrThrow()
                ready = true
                connected = true
                _uiState.update { it.copy(apiKey = "") }
                onSaved(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(error = ((error as? GeckoException)?.error
                        ?: GeckoError(ErrorKind.Unknown, error.message)).copy(providerLabel = state.providerLabel))
                }
            } finally {
                // Failed setup stays on the same form with the pasted key intact, ready to retry.
                // It must not leave an unusable provider or a false "connected" state behind.
                if (!ready) createdId?.let { id ->
                    withContext(NonCancellable) { providerConfigRepository.removeProvider(id) }
                }
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
