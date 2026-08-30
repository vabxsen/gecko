package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository

class TestProviderConnectionUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        providerConfigRepository.setConnectionStatus(id, ConnectionStatus.Testing)
        val result = chatCompletionRepository.testConnection(id)
        providerConfigRepository.setConnectionStatus(
            id,
            result.fold(
                onSuccess = { ConnectionStatus.Success },
                onFailure = { ConnectionStatus.Failure(it.message ?: "Connection failed") },
            ),
        )
        return result
    }
}
