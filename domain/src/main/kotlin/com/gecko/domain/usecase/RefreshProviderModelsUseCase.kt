package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ModelInfo
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository

class RefreshProviderModelsUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(id: String): Result<List<ModelInfo>> {
        val result = chatCompletionRepository.fetchModels(id)
        result.onSuccess { models -> providerConfigRepository.saveModels(id, models) }
        return result
    }
}
