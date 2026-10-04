package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ModelInfo
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.CancellationException

class RefreshProviderModelsUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(id: String): Result<List<ModelInfo>> {
        return try {
            chatCompletionRepository.fetchModels(id).also { result ->
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                result.onSuccess { models -> providerConfigRepository.saveModels(id, models) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
