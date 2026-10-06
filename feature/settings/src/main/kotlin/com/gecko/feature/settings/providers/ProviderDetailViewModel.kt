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
import com.gecko.domain.repository.SecureKeyRepository
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import com.gecko.domain.usecase.ConnectProviderUseCase
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
    val isSavingKey: Boolean = false,
    val apiKeyValue: String? = null,
    val isApiKeyLoaded: Boolean = false,
    val saveKeyErrorMessage: String? = null,
    /** The model verified for this connection, independent of the currently active key. */
    val selectedModelId: String? = null,
    val isLoading: Boolean = true,
    val lastCheckedAt: java.time.Instant? = null,
    val checkDurationMs: Long? = null,
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
)

@HiltViewModel
class ProviderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerConfigRepository: ProviderConfigRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val saveProviderApiKeyUseCase: SaveProviderApiKeyUseCase,
    private val connectProviderUseCase: ConnectProviderUseCase,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>("configId")
        ?: savedStateHandle.toRoute<ProviderDetailRoute>().configId

    private val isSavingKey = MutableStateFlow(false)
    private data class CheckResult(val time: java.time.Instant, val durationMs: Long)
    private val lastCheck = MutableStateFlow<CheckResult?>(null)

    private suspend fun checkConnection(activate: Boolean = true): Result<ModelInfo> {
        val start = System.nanoTime()
        try {
            return connectProviderUseCase(id, activate)
        } finally {
            lastCheck.value = CheckResult(java.time.Instant.now(), (System.nanoTime() - start) / 1_000_000)
        }
    }

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
        isSavingKey,
        // Grouped because combine only has typed overloads up to five flows, and the untyped
        // vararg version loses every type in the lambda.
        combine(apiKeyValue, isApiKeyLoaded, saveKeyErrorMessage, ::KeyEditingState),
        lastCheck,
    ) { config, models, savingKey, keyState, check ->
        ProviderDetailUiState(
            id = id,
            config = config,
            availableModels = models,
            isSavingKey = savingKey,
            apiKeyValue = keyState.value,
            isApiKeyLoaded = keyState.loaded,
            saveKeyErrorMessage = keyState.error,
            selectedModelId = config?.verifiedModelId,
            isLoading = false,
            lastCheckedAt = check?.time,
            checkDurationMs = check?.durationMs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderDetailUiState(id = id))

    fun setEnabled(enabled: Boolean) {
        if (isSavingKey.value) return
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
                checkConnection().getOrThrow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                saveKeyErrorMessage.value = (error as? GeckoException)?.error?.copyForUser()?.explanation
                    ?: "Couldn't save or check this key. Please try again."
            } finally {
                isSavingKey.value = false
            }
        }
    }

    fun clearApiKey() {
        if (isSavingKey.value) return
        viewModelScope.launch {
            secureKeyRepository.clearApiKey(id)
            providerConfigRepository.setVerifiedModel(id, null)
            providerConfigRepository.setConnectionStatus(id, ConnectionStatus.Untested)
            apiKeyValue.value = null
            saveKeyErrorMessage.value = null
            lastCheck.value = null
        }
    }

    fun testConnection() {
        recheckConnection(activate = true)
    }

    fun runDiagnostics() {
        recheckConnection(activate = false)
    }

    private fun recheckConnection(activate: Boolean) {
        if (isSavingKey.value || uiState.value.connectionStatus == ConnectionStatus.Testing) return
        isSavingKey.value = true
        saveKeyErrorMessage.value = null
        viewModelScope.launch {
            try { checkConnection(activate) }
            finally { isSavingKey.value = false }
        }
    }

    fun setBaseUrlOverride(url: String?) {
        if (isSavingKey.value) return
        isSavingKey.value = true
        saveKeyErrorMessage.value = null
        viewModelScope.launch {
            try {
                providerConfigRepository.setBaseUrlOverride(id, url?.trim()?.trimEnd('/')?.ifBlank { null })
                providerConfigRepository.saveModels(id, emptyList())
                checkConnection()
            } finally { isSavingKey.value = false }
        }
    }

    fun deleteProvider(onDeleted: () -> Unit) {
        if (isSavingKey.value) return
        viewModelScope.launch {
            providerConfigRepository.removeProvider(id)
            onDeleted()
        }
    }
}
