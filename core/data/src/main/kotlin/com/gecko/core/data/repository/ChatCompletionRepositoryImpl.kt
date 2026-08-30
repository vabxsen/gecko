package com.gecko.core.data.repository

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.provider.api.ProviderFactory
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.SecureKeyRepository
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
        configId: String,
        modelId: String,
        history: List<ChatMessage>,
        stream: Boolean,
    ): Flow<ChatEvent> {
        val config = providerConfigRepository.observe(configId).first()
            ?: return flowOf(ChatEvent.Error(message = "This API key was removed.", cause = null, isRetryable = false))
        val apiKey = secureKeyRepository.getApiKey(configId)
        if (apiKey.isNullOrBlank()) {
            return flowOf(
                ChatEvent.Error(
                    message = "No API key set for ${config.displayLabel}. Add one in Settings.",
                    cause = null,
                    isRetryable = false,
                ),
            )
        }
        return providerFactory.create(config.providerId, apiKey, config.baseUrlOverride).sendMessage(history, modelId, stream)
    }

    override suspend fun testConnection(configId: String): Result<Unit> {
        val config = providerConfigRepository.observe(configId).first()
            ?: return Result.failure(IllegalStateException("This API key was removed."))
        val apiKey = secureKeyRepository.getApiKey(configId)
            ?: return Result.failure(IllegalStateException("No API key set"))
        return providerFactory.create(config.providerId, apiKey, config.baseUrlOverride).testConnection()
    }

    override suspend fun fetchModels(configId: String): Result<List<ModelInfo>> {
        val config = providerConfigRepository.observe(configId).first()
            ?: return Result.failure(IllegalStateException("This API key was removed."))
        val apiKey = secureKeyRepository.getApiKey(configId)
            ?: return Result.failure(IllegalStateException("No API key set"))
        return providerFactory.create(config.providerId, apiKey, config.baseUrlOverride).listModels()
    }

    private val ProviderConfig.displayLabel: String get() = label.ifBlank { providerId.displayName }
}
