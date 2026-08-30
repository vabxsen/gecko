package com.orca.feature.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orca.core.model.provider.ProviderConfig
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ProviderConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val providerConfigRepository: ProviderConfigRepository,
) : ViewModel() {

    val providerConfigs: StateFlow<List<ProviderConfig>> = providerConfigRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(providerId: ProviderId, enabled: Boolean) {
        viewModelScope.launch { providerConfigRepository.setEnabled(providerId, enabled) }
    }
}
