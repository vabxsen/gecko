package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository

class RefreshProviderModelsUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(providerId: ProviderId): Result<List<ModelInfo>> {
        val result = chatCompletionRepository.fetchModels(providerId)
        result.onSuccess { models -> providerConfigRepository.saveModels(providerId, models) }
        return result
    }
}
