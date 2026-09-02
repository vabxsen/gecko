package com.gecko.core.data.repository

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.provider.api.ProviderFactory
import com.gecko.domain.model.curatedForSelection
import com.gecko.domain.model.trimToContextBudget
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
            ?: return flowOf(ChatEvent.Error(GeckoError(ErrorKind.KeyRemoved, configId = configId)))
        val apiKey = secureKeyRepository.getApiKey(configId)
        if (apiKey.isNullOrBlank()) {
            return flowOf(ChatEvent.Error(config.missingKeyError(configId)))
        }
        val contextWindowTokens = providerConfigRepository.observeModels(configId).first()
            .find { it.modelId == modelId }?.contextWindowTokens
        val budgetedHistory = history.trimToContextBudget(contextWindowTokens)
        return providerFactory.create(config.providerId, apiKey, config.baseUrlOverride).sendMessage(budgetedHistory, modelId, stream)
    }

    /**
     * Two steps, because neither alone is enough. Listing models catches an unreachable endpoint
     * and every provider that authenticates its catalog; a real one-word completion catches the
     * ones that don't — NVIDIA NIM serves `/v1/models` to anyone, so a fake key used to be saved
     * and shown as "Connected" until the user's first message failed.
     *
     * Only an authentication failure on the probe fails the test. A rate limit or a quota error
     * means the key is genuinely valid and something else is wrong, and refusing to save a working
     * key over a temporary 429 would be its own bug.
     */
    override suspend fun testConnection(configId: String): Result<Unit> {
        val config = providerConfigRepository.observe(configId).first()
            ?: return Result.failure(GeckoException(GeckoError(ErrorKind.KeyRemoved, configId = configId)))
        val apiKey = secureKeyRepository.getApiKey(configId)
            ?: return Result.failure(GeckoException(config.missingKeyError(configId)))
        val provider = providerFactory.create(config.providerId, apiKey, config.baseUrlOverride)

        val models = provider.listModels().getOrElse { return Result.failure(it) }
        val probeModel = models.curatedForSelection(config.providerId, config.baseUrlOverride)
            .defaultChoice?.modelId
            ?: models.firstOrNull()?.modelId
            ?: return Result.success(Unit)

        val failure = provider.probeChat(probeModel)
        return if (failure != null && failure.error.kind == ErrorKind.InvalidApiKey) {
            Result.failure(GeckoException(failure.error))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun fetchModels(configId: String): Result<List<ModelInfo>> {
        val config = providerConfigRepository.observe(configId).first()
            ?: return Result.failure(GeckoException(GeckoError(ErrorKind.KeyRemoved, configId = configId)))
        val apiKey = secureKeyRepository.getApiKey(configId)
            ?: return Result.failure(GeckoException(config.missingKeyError(configId)))
        return providerFactory.create(config.providerId, apiKey, config.baseUrlOverride).listModels()
    }

    private val ProviderConfig.displayLabel: String get() = label.ifBlank { providerId.displayName }

    /**
     * "No key" and "a key we can no longer decrypt" look identical from here — the keystore
     * returns null for both — but they need opposite advice, so [SecureKeyRepository.hasApiKey]
     * (which only checks that ciphertext exists) tells them apart. Someone whose key stopped
     * decrypting after a screen-lock change was previously told to go and add the key they
     * already had.
     */
    private suspend fun ProviderConfig.missingKeyError(configId: String) = GeckoError(
        kind = if (secureKeyRepository.hasApiKey(configId)) {
            ErrorKind.UndecryptableKey
        } else {
            ErrorKind.NoApiKey
        },
        configId = configId,
        providerLabel = displayLabel,
    )
}
