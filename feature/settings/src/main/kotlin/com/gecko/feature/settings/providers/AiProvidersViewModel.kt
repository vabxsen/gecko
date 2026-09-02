package com.gecko.feature.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.model.friendlyName
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One saved key as the list shows it: which model it's set to, and whether it's the one currently
 * in use. Both used to live on a separate screen, which is what made the split confusing —
 * "AI Providers" listed keys and "Model preferences" listed the same keys again, with the model.
 */
data class ProviderRowState(
    val config: ProviderConfig,
    val modelLabel: String?,
    val isInUse: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val providerConfigs = providerConfigRepository.observeAll()

    /**
     * Every saved key's cached catalog at once, so a row can name its model without a per-row
     * fetch. Re-subscribes only when the *set of ids* changes — not on every unrelated config edit
     * such as a renamed label or a connection-status write — so an in-flight observation isn't
     * torn down and restarted needlessly. Same shape as ChatViewModel's; keep them in step.
     */
    private val modelNames: Flow<Map<String, Map<String, String>>> = providerConfigs
        .map { configs -> configs.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    ids.map { id ->
                        providerConfigRepository.observeModels(id)
                            .map { models -> id to models.associate { it.modelId to it.friendlyName } }
                    },
                ) { it.toMap() }
            }
        }

    val uiState: StateFlow<List<ProviderRowState>> = combine(
        providerConfigs,
        userPreferencesRepository.userPreferences,
        modelNames,
    ) { configs, prefs, names ->
        configs.map { config ->
            val isInUse = config.id == prefs.defaultProviderConfigId
            ProviderRowState(
                config = config,
                // Only the key in use has a model — the preference is app-wide, so claiming a
                // model for a key that isn't selected would be inventing one.
                modelLabel = prefs.defaultModelId
                    ?.takeIf { isInUse }
                    ?.let { names[config.id]?.get(it) ?: it },
                isInUse = isInUse,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { providerConfigRepository.setEnabled(id, enabled) }
    }
}
