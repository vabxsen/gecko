package com.orca.domain.usecase

import javax.inject.Inject

import com.orca.core.model.provider.ConnectionStatus
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ChatCompletionRepository
import com.orca.domain.repository.ProviderConfigRepository

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
