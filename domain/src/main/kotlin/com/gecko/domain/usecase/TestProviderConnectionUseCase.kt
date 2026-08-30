package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository

class TestProviderConnectionUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(providerId: ProviderId): Result<Unit> {
        providerConfigRepository.setConnectionStatus(providerId, ConnectionStatus.Testing)
        val result = chatCompletionRepository.testConnection(providerId)
        providerConfigRepository.setConnectionStatus(
            providerId,
            result.fold(
                onSuccess = { ConnectionStatus.Success },
                onFailure = { ConnectionStatus.Failure(it.message ?: "Connection failed") },
            ),
        )
        return result
    }
}
