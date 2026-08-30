package com.orca.core.data.repository

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderId
import com.orca.core.provider.api.ProviderFactory
import com.orca.domain.repository.ChatCompletionRepository
import com.orca.domain.repository.ProviderConfigRepository
import com.orca.domain.repository.SecureKeyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

class ChatCompletionRepositoryImpl @Inject constructor(
    private val secureKeyRepository: SecureKeyRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val providerFactory: ProviderFactory,
) : ChatCompletionRepository {

    override suspend fun sendMessage(
        providerId: ProviderId,
        modelId: String,
        history: List<ChatMessage>,
        stream: Boolean,
    ): Flow<ChatEvent> {
        val apiKey = secureKeyRepository.getApiKey(providerId)
        if (apiKey.isNullOrBlank()) {
            return flowOf(
                ChatEvent.Error(
                    message = "No API key set for ${providerId.displayName}. Add one in Settings.",
                    cause = null,
                    isRetryable = false,
                ),
            )
        }
        val baseUrl = providerConfigRepository.observe(providerId).first().baseUrlOverride
        return providerFactory.create(providerId, apiKey, baseUrl).sendMessage(history, modelId, stream)
    }

    override suspend fun testConnection(providerId: ProviderId): Result<Unit> {
        val apiKey = secureKeyRepository.getApiKey(providerId)
            ?: return Result.failure(IllegalStateException("No API key set"))
        val baseUrl = providerConfigRepository.observe(providerId).first().baseUrlOverride
        return providerFactory.create(providerId, apiKey, baseUrl).testConnection()
    }

    override suspend fun fetchModels(providerId: ProviderId): Result<List<ModelInfo>> {
        val apiKey = secureKeyRepository.getApiKey(providerId)
            ?: return Result.failure(IllegalStateException("No API key set"))
        val baseUrl = providerConfigRepository.observe(providerId).first().baseUrlOverride
        return providerFactory.create(providerId, apiKey, baseUrl).listModels()
    }
}
