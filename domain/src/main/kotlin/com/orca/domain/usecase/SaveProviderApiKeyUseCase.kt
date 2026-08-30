package com.orca.domain.usecase

import com.orca.core.model.provider.ConnectionStatus
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ProviderConfigRepository
import com.orca.domain.repository.SecureKeyRepository

class SaveProviderApiKeyUseCase(
    private val secureKeyRepository: SecureKeyRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(providerId: ProviderId, apiKey: String) {
        secureKeyRepository.saveApiKey(providerId, apiKey)
        providerConfigRepository.setConnectionStatus(providerId, ConnectionStatus.Untested)
    }
}
