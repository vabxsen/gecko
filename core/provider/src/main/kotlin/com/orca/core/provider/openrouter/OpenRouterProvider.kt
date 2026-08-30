package com.orca.core.provider.openrouter

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderId
import com.orca.core.provider.api.AiProvider
import com.orca.core.provider.internal.ProviderHttpException
import com.orca.core.provider.internal.ProviderJson
import com.orca.core.provider.internal.bodyOrThrow
import com.orca.core.provider.openai.OpenAiChatEngine
import com.orca.core.provider.openai.toReadableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenRouter speaks the same wire format as OpenAI's chat-completions API, so streaming and
 * message send is delegated to [OpenAiChatEngine]. Only auth headers, base URL, and the model
 * catalog shape differ.
 */
class OpenRouterProvider(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient,
) : AiProvider {

    override val id: ProviderId = ProviderId.OPENROUTER

    private val engine = OpenAiChatEngine(
        httpClient = httpClient,
        chatCompletionsUrl = "$baseUrl/chat/completions",
        applyHeaders = {
            header("Authorization", "Bearer $apiKey")
            header("X-Title", "Orca")
        },
    )

    override suspend fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent> =
        engine.sendMessage(messages, model, stream)

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }

            val parsed = ProviderJson.decodeFromString(OpenRouterModelsResponse.serializer(), bodyText)
            parsed.data.map { model ->
                ModelInfo(
                    providerId = ProviderId.OPENROUTER,
                    modelId = model.id,
                    displayName = model.name,
                    contextWindowTokens = model.contextLength,
                    supportsStreaming = true,
                    supportsImages = model.architecture.modality.contains("image"),
                )
            }
        }
    }

    override suspend fun testConnection(): Result<Unit> = listModels().map { }

    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
    }
}
