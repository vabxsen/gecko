package com.gecko.core.provider.google

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

class GoogleGeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient,
) : AiProvider {

    override val id: ProviderId = ProviderId.GOOGLE

    override suspend fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent> = flow {
        emit(ChatEvent.Started())

        val systemPrompt = messages.filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }
        val contents = messages.filter { it.role != MessageRole.SYSTEM }
            .map {
                GeminiContent(
                    role = if (it.role == MessageRole.ASSISTANT) "model" else "user",
                    parts = buildList {
                        if (it.content.isNotEmpty()) add(GeminiPart(text = it.content))
                        it.attachmentImageBase64?.let { image ->
                            add(GeminiPart(inlineData = GeminiInlineData(mimeType = JPEG_MIME_TYPE, data = image)))
                        }
                    },
                )
            }
        val requestBody = GeminiRequest(
            contents = contents,
            systemInstruction = systemPrompt?.let { GeminiSystemInstruction(listOf(GeminiPart(it))) },
            // Only the dedicated image-output models (their ids all contain "image", e.g.
            // gemini-2.5-flash-image) need this — sending it to a normal chat model risks the
            // API rejecting a response modality that model doesn't support, so every other
            // model's request stays exactly as it was before.
            generationConfig = if (model.contains("image", ignoreCase = true)) {
                GeminiGenerationConfig(responseModalities = listOf("TEXT", "IMAGE"))
            } else {
                null
            },
        )

        val endpoint = if (stream) "streamGenerateContent" else "generateContent"
        val url = if (stream) "$baseUrl/models/$model:$endpoint?alt=sse" else "$baseUrl/models/$model:$endpoint"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(ProviderJson.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        if (stream) {
            var attempt = 0
            var contentReceived = false
            while (true) {
                attempt++
                var finishReason: FinishReason? = null
                var usage: TokenUsage? = null
                try {
                    httpClient.streamSse(request).collect { event ->
                        if (event.data.isBlank()) return@collect
                        val parsed = event.data.decodeResponseOrThrow() ?: return@collect
                        parsed.throwIfRefused()

                        parsed.candidates.firstOrNull()?.let { candidate ->
                            candidate.content.parts.forEach { part ->
                                part.text?.takeIf { it.isNotEmpty() }?.let {
                                    contentReceived = true
                                    emit(ChatEvent.ContentDelta(it))
                                }
                                part.inlineData?.let {
                                    contentReceived = true
                                    emit(ChatEvent.ImageDelta(base64 = it.data, mimeType = it.mimeType))
                                }
                            }
                            candidate.finishReason?.let { finishReason = it.toFinishReason() }
                        }
                        parsed.usageMetadata?.let {
                            usage = TokenUsage(it.promptTokenCount, it.candidatesTokenCount, it.totalTokenCount)
                        }
                    }
                    if (!contentReceived) throw EmptyProviderResponseException()
                    emit(ChatEvent.Completed(finishReason ?: FinishReason.STOP, usage))
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
            val parsed = ProviderJson.decodeFromString(GeminiGenerateContentResponse.serializer(), bodyText)
            parsed.throwIfRefused()
            val candidate = parsed.candidates.firstOrNull() ?: throw EmptyProviderResponseException()
            var emittedAnything = false
            candidate.content.parts.forEach { part ->
                part.text?.takeIf { it.isNotEmpty() }?.let {
                    emittedAnything = true
                    emit(ChatEvent.ContentDelta(it))
                }
                part.inlineData?.let {
                    emittedAnything = true
                    emit(ChatEvent.ImageDelta(base64 = it.data, mimeType = it.mimeType))
                }
            }
            if (!emittedAnything) throw EmptyProviderResponseException()
            val usage = parsed.usageMetadata?.let { TokenUsage(it.promptTokenCount, it.candidatesTokenCount, it.totalTokenCount) }
            emit(ChatEvent.Completed(candidate.finishReason?.toFinishReason() ?: FinishReason.STOP, usage))
        }
    }.catch { e ->
        emit(ChatEvent.Error(error = e.toGeckoError(), cause = e))
    }

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        val collected = mutableListOf<GeminiModel>()
        var pageToken: String? = null
        var page = 0
        do {
            val url = buildString {
                append("$baseUrl/models?pageSize=$MODELS_PAGE_SIZE")
                pageToken?.let { append("&pageToken=$it") }
            }
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            val response = httpClient.executeWithRetry(request)
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }

            val parsed = ProviderJson.decodeFromString(GeminiModelsResponse.serializer(), bodyText)
            collected += parsed.models
            pageToken = parsed.nextPageToken?.takeIf { it.isNotBlank() }
            page++
        } while (pageToken != null && page < MAX_MODEL_PAGES)

        collected
            .filter { it.supportedGenerationMethods.contains("generateContent") }
            .map { model ->
                ModelInfo(
                    providerId = ProviderId.GOOGLE,
                    modelId = model.name.removePrefix("models/"),
                    displayName = model.displayName,
                    contextWindowTokens = model.inputTokenLimit,
                    supportsStreaming = model.supportedGenerationMethods.contains("streamGenerateContent"),
                    supportsImages = true,
                )
            }
    }

    override suspend fun testConnection(): Result<Unit> = listModels().map { }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        /** Google caps this at 1000; 200 keeps the whole catalog to a page or two in practice. */
        private const val MODELS_PAGE_SIZE = 200
        /** A guard against a malformed token looping forever, not an expected limit. */
        private const val MAX_MODEL_PAGES = 10
    }
}

/**
 * Decodes one `data:` frame, giving anything that isn't a generate-content response a second
 * reading as a bare error object before discarding it. Dropping unrecognised frames outright is
 * how a mid-stream failure used to end as a silent, empty reply.
 */
private fun String.decodeResponseOrThrow(): GeminiGenerateContentResponse? {
    runCatching { ProviderJson.decodeFromString(GeminiGenerateContentResponse.serializer(), this) }
        .getOrNull()
        ?.let { return it }
    runCatching { ProviderJson.decodeFromString(GeminiErrorResponse.serializer(), this) }
        .getOrNull()
        ?.error
        ?.let { throw ProviderReportedException(code = null, message = it.message) }
    return null
}

/** Raises the two ways Gemini says "no" inside a 200 response: an error object, or a blocked prompt. */
private fun GeminiGenerateContentResponse.throwIfRefused() {
    error?.let { throw ProviderReportedException(code = null, message = it.message) }
    promptFeedback?.blockReason?.let {
        throw ProviderReportedException(code = null, message = "$BLOCKED_PROMPT_PREFIX$it")
    }
}

internal const val BLOCKED_PROMPT_PREFIX = "Blocked by Google's safety filters: "

private fun String.toFinishReason(): FinishReason = when (this) {
    "STOP" -> FinishReason.STOP
    "MAX_TOKENS" -> FinishReason.LENGTH
    "SAFETY", "RECITATION" -> FinishReason.CONTENT_FILTER
    else -> FinishReason.STOP
}

private fun ProviderHttpException.toReadableException(): GeckoException {
    val parsedMessage = runCatching {
        ProviderJson.decodeFromString(GeminiErrorResponse.serializer(), message.orEmpty()).error?.message
    }.getOrNull()
    return GeckoException(
        classifyProviderError(
            statusCode = code,
            detail = parsedMessage?.takeIf { it.isNotBlank() } ?: message,
            cause = this,
        ),
    )
}
