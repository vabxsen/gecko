package com.gecko.core.provider.anthropic

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.network.sse.SseException
import com.gecko.core.network.sse.streamSse
import com.gecko.core.provider.api.AiProvider
import com.gecko.core.provider.internal.ProviderHttpException
import com.gecko.core.provider.internal.ProviderJson
import com.gecko.core.provider.internal.bodyOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
) : AiProvider {

    override val id: ProviderId = ProviderId.ANTHROPIC

    override suspend fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent> = flow {
        emit(ChatEvent.Started())

        val systemPrompt = messages.filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }
        val conversation = messages.filter { it.role != MessageRole.SYSTEM }
            .map { AnthropicMessage(role = if (it.role == MessageRole.ASSISTANT) "assistant" else "user", content = it.content) }

        val requestBody = AnthropicRequest(
            model = model,
            maxTokens = maxTokens,
            system = systemPrompt,
            messages = conversation,
            stream = stream,
        )
        val request = Request.Builder()
            .url("$baseUrl/messages")
            .applyAuthHeaders(apiKey)
            .post(ProviderJson.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        if (stream) {
            var inputTokens = 0
            var outputTokens = 0
            var finishReason: FinishReason? = null
            var hadError = false

            httpClient.streamSse(request).collect { event ->
                if (event.data.isBlank()) return@collect
                val parsed = runCatching { ProviderJson.decodeFromString(AnthropicStreamEvent.serializer(), event.data) }
                    .getOrNull() ?: return@collect

                when (parsed.type) {
                    "message_start" -> inputTokens = parsed.message?.usage?.inputTokens ?: inputTokens
                    "content_block_delta" -> parsed.delta?.text?.let { text ->
                        if (text.isNotEmpty()) emit(ChatEvent.ContentDelta(text))
                    }
                    "message_delta" -> {
                        parsed.delta?.stopReason?.let { finishReason = it.toFinishReason() }
                        parsed.usage?.let { outputTokens = it.outputTokens }
                    }
                    "error" -> {
                        hadError = true
                        emit(
                            ChatEvent.Error(
                                message = parsed.error?.message?.takeIf { it.isNotBlank() } ?: "Anthropic stream error",
                                cause = null,
                                isRetryable = true,
                            ),
                        )
                    }
                }
            }

            if (!hadError) {
                emit(ChatEvent.Completed(finishReason ?: FinishReason.STOP, TokenUsage(inputTokens, outputTokens, inputTokens + outputTokens)))
            }
        } else {
            val response = httpClient.newCall(request).execute()
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }
            val parsed = ProviderJson.decodeFromString(AnthropicResponse.serializer(), bodyText)
            val text = parsed.content.firstOrNull { it.type == "text" }?.text
            if (!text.isNullOrEmpty()) emit(ChatEvent.ContentDelta(text))
            val usage = parsed.usage?.let { TokenUsage(it.inputTokens, it.outputTokens, it.inputTokens + it.outputTokens) }
            emit(ChatEvent.Completed(parsed.stopReason?.toFinishReason() ?: FinishReason.STOP, usage))
        }
    }.catch { e ->
        val message = when (e) {
            is SseException -> e.message ?: "Stream failed"
            else -> e.message ?: "Request failed"
        }
        emit(ChatEvent.Error(message = message, cause = e, isRetryable = true))
    }

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .applyAuthHeaders(apiKey)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }

            val parsed = ProviderJson.decodeFromString(AnthropicModelsResponse.serializer(), bodyText)
            parsed.data.map { model ->
                ModelInfo(
                    providerId = ProviderId.ANTHROPIC,
                    modelId = model.id,
                    displayName = model.displayName,
                    contextWindowTokens = AnthropicModelCatalog.contextWindowFor(model.id),
                    supportsStreaming = true,
                    supportsImages = AnthropicModelCatalog.supportsImagesFor(model.id),
                )
            }
        }
    }

    override suspend fun testConnection(): Result<Unit> = listModels().map { }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com/v1"
        const val DEFAULT_MAX_TOKENS = 4096
        private const val ANTHROPIC_VERSION = "2023-06-01"

        internal fun Request.Builder.applyAuthHeaders(apiKey: String): Request.Builder = this
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
    }
}

private fun String.toFinishReason(): FinishReason = when (this) {
    "end_turn", "stop_sequence" -> FinishReason.STOP
    "max_tokens" -> FinishReason.LENGTH
    else -> FinishReason.STOP
}

private fun ProviderHttpException.toReadableException(): Exception {
    val parsedMessage = runCatching {
        ProviderJson.decodeFromString(AnthropicErrorResponse.serializer(), message.orEmpty()).error?.message
    }.getOrNull()
    return Exception(parsedMessage?.takeIf { it.isNotBlank() } ?: message, this)
}
