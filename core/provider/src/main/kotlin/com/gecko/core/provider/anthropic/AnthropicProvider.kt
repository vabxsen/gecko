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
import com.gecko.core.provider.internal.EmptyProviderResponseException
import com.gecko.core.model.error.GeckoException
import com.gecko.core.provider.internal.ProviderHttpException
import com.gecko.core.provider.internal.ProviderJson
import com.gecko.core.provider.internal.ProviderReportedException
import com.gecko.core.provider.internal.RetryPolicy
import com.gecko.core.provider.internal.bodyOrThrow
import com.gecko.core.provider.internal.classifyProviderError
import com.gecko.core.provider.internal.executeWithRetry
import com.gecko.core.provider.internal.toGeckoError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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
) : AiProvider {

    override val id: ProviderId = ProviderId.ANTHROPIC

    override suspend fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent> = flow {
        emit(ChatEvent.Started())

        val systemPrompt = messages.filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }
        val conversation = messages.filter { it.role != MessageRole.SYSTEM }
            .map { message ->
                AnthropicMessage(
                    role = if (message.role == MessageRole.ASSISTANT) "assistant" else "user",
                    content = buildList {
                        if (message.content.isNotEmpty()) add(AnthropicContentBlock(text = message.content))
                        message.attachmentImageBase64?.let { image ->
                            add(
                                AnthropicContentBlock(
                                    type = "image",
                                    source = AnthropicImageSource(mediaType = JPEG_MIME_TYPE, data = image),
                                ),
                            )
                        }
                    },
                )
            }

        val requestBody = AnthropicRequest(
            model = model,
            maxTokens = AnthropicModelCatalog.maxOutputTokensFor(model),
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
            var attempt = 0
            var contentReceived = false
            while (true) {
                attempt++
                var inputTokens = 0
                var outputTokens = 0
                var finishReason: FinishReason? = null
                try {
                    httpClient.streamSse(request).collect { event ->
                        if (event.data.isBlank()) return@collect
                        val parsed = runCatching { ProviderJson.decodeFromString(AnthropicStreamEvent.serializer(), event.data) }
                            .getOrNull() ?: return@collect

                        when (parsed.type) {
                            "message_start" -> inputTokens = parsed.message?.usage?.inputTokens ?: inputTokens
                            "content_block_delta" -> parsed.delta?.text?.let { text ->
                                if (text.isNotEmpty()) {
                                    contentReceived = true
                                    emit(ChatEvent.ContentDelta(text))
                                }
                            }
                            "message_delta" -> {
                                parsed.delta?.stopReason?.let { finishReason = it.toFinishReason() }
                                parsed.usage?.let { outputTokens = it.outputTokens }
                            }
                            // Thrown rather than emitted so it lands in the same terminal catch as
                            // every other failure: emitting an Error inline built a second, subtly
                            // different error path that lost the status code and could never be
                            // classified or retried like the rest.
                            "error" -> throw ProviderReportedException(
                                code = null,
                                message = parsed.error?.message?.takeIf { it.isNotBlank() }
                                    ?: "Anthropic reported an error.",
                            )
                        }
                    }
                    if (!contentReceived) throw EmptyProviderResponseException()
                    emit(ChatEvent.Completed(finishReason ?: FinishReason.STOP, TokenUsage(inputTokens, outputTokens, inputTokens + outputTokens)))
                    break
                } catch (e: Exception) {
                    if (contentReceived || attempt >= RetryPolicy.MAX_ATTEMPTS || !RetryPolicy.isRetryableThrowable(e)) throw e
                    delay(RetryPolicy.backoffDelayMillis(attempt))
                }
            }
        } else {
            val response = httpClient.executeWithRetry(request)
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }
            val parsed = ProviderJson.decodeFromString(AnthropicResponse.serializer(), bodyText)
            val text = parsed.content.firstOrNull { it.type == "text" }?.text
            if (text.isNullOrEmpty()) throw EmptyProviderResponseException()
            emit(ChatEvent.ContentDelta(text))
            val usage = parsed.usage?.let { TokenUsage(it.inputTokens, it.outputTokens, it.inputTokens + it.outputTokens) }
            emit(ChatEvent.Completed(parsed.stopReason?.toFinishReason() ?: FinishReason.STOP, usage))
        }
    }.catch { e ->
        emit(ChatEvent.Error(error = e.toGeckoError(), cause = e))
    }

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/models")
            .applyAuthHeaders(apiKey)
            .get()
            .build()

        val response = httpClient.executeWithRetry(request)
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

    override suspend fun testConnection(): Result<Unit> = listModels().map { }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com/v1"
        private const val JPEG_MIME_TYPE = "image/jpeg"
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

private fun ProviderHttpException.toReadableException(): GeckoException {
    val parsedMessage = runCatching {
        ProviderJson.decodeFromString(AnthropicErrorResponse.serializer(), message.orEmpty()).error?.message
    }.getOrNull()
    return GeckoException(
        classifyProviderError(
            statusCode = code,
            detail = parsedMessage?.takeIf { it.isNotBlank() } ?: message,
            cause = this,
        ),
    )
}
