package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.SecureKeyRepository

class SaveProviderApiKeyUseCase @Inject constructor(
    private val secureKeyRepository: SecureKeyRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(providerId: ProviderId, apiKey: String) {
        secureKeyRepository.saveApiKey(providerId, apiKey)
        providerConfigRepository.setConnectionStatus(providerId, ConnectionStatus.Untested)
    }
}
