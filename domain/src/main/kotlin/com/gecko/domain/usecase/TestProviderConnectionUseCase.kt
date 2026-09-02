package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
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
                // The provider already classified this; flattening it back to a string here is
                // what made Settings show raw vendor JSON where chat showed a readable sentence.
                onFailure = {
                    ConnectionStatus.Failure(
                        (it as? GeckoException)?.error
                            ?: GeckoError(ErrorKind.Unknown, technicalDetail = it.message),
                    )
                },
            ),
        )
        return result
    }
}
