package com.gecko.core.provider.openai

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.provider.api.AiProvider
import com.gecko.core.provider.internal.ProviderHttpException
import com.gecko.core.provider.internal.ProviderJson
import com.gecko.core.provider.internal.bodyOrThrow
import com.gecko.core.provider.internal.executeWithRetry
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenAiProvider(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient,
) : AiProvider {

    override val id: ProviderId = ProviderId.OPENAI

    private val engine = OpenAiChatEngine(
        httpClient = httpClient,
        chatCompletionsUrl = "$baseUrl/chat/completions",
        applyHeaders = { header("Authorization", "Bearer $apiKey") },
    )

    override suspend fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent> =
        engine.sendMessage(messages, model, stream)

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = httpClient.executeWithRetry(request)
        val bodyText = try {
            response.bodyOrThrow()
        } catch (e: ProviderHttpException) {
            throw e.toReadableException()
        }

        val parsed = ProviderJson.decodeFromString(OpenAiModelsResponse.serializer(), bodyText)
        parsed.data.map { model ->
            val meta = OpenAiModelCatalog.metadataFor(model.id)
            ModelInfo(
                providerId = ProviderId.OPENAI,
                modelId = model.id,
                displayName = model.id,
                contextWindowTokens = meta.contextWindow,
                supportsStreaming = true,
                supportsImages = meta.supportsImages,
            )
        }
    }

    override suspend fun testConnection(): Result<Unit> = listModels().map { }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}
