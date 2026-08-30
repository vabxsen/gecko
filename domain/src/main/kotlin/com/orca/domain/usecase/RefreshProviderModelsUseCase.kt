package com.orca.domain.usecase

import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ChatCompletionRepository
import com.orca.domain.repository.ProviderConfigRepository

class RefreshProviderModelsUseCase(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val providerConfigRepository: ProviderConfigRepository,
) {
    suspend operator fun invoke(providerId: ProviderId): Result<List<ModelInfo>> {
        val result = chatCompletionRepository.fetchModels(providerId)
        result.onSuccess { models -> providerConfigRepository.saveModels(providerId, models) }
        return result
    }
}
