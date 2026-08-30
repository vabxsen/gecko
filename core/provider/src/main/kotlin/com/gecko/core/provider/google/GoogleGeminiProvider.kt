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
                    parts = listOf(GeminiPart(it.content)),
                )
            }
        val requestBody = GeminiRequest(
            contents = contents,
            systemInstruction = systemPrompt?.let { GeminiSystemInstruction(listOf(GeminiPart(it))) },
        )

        val endpoint = if (stream) "streamGenerateContent" else "generateContent"
        val url = if (stream) "$baseUrl/models/$model:$endpoint?alt=sse" else "$baseUrl/models/$model:$endpoint"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(ProviderJson.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        if (stream) {
            var finishReason: FinishReason? = null
            var usage: TokenUsage? = null

            httpClient.streamSse(request).collect { event ->
                if (event.data.isBlank()) return@collect
                val parsed = runCatching {
                    ProviderJson.decodeFromString(GeminiGenerateContentResponse.serializer(), event.data)
                }.getOrNull() ?: return@collect

                parsed.candidates.firstOrNull()?.let { candidate ->
                    candidate.content.parts.firstOrNull()?.text?.let { text ->
                        if (text.isNotEmpty()) emit(ChatEvent.ContentDelta(text))
                    }
                    candidate.finishReason?.let { finishReason = it.toFinishReason() }
                }
                parsed.usageMetadata?.let {
                    usage = TokenUsage(it.promptTokenCount, it.candidatesTokenCount, it.totalTokenCount)
                }
            }

            emit(ChatEvent.Completed(finishReason ?: FinishReason.STOP, usage))
        } else {
            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }
            val parsed = ProviderJson.decodeFromString(GeminiGenerateContentResponse.serializer(), bodyText)
            val candidate = parsed.candidates.firstOrNull()
            candidate?.content?.parts?.firstOrNull()?.text?.let { text ->
                if (text.isNotEmpty()) emit(ChatEvent.ContentDelta(text))
            }
            val usage = parsed.usageMetadata?.let { TokenUsage(it.promptTokenCount, it.candidatesTokenCount, it.totalTokenCount) }
            emit(ChatEvent.Completed(candidate?.finishReason?.toFinishReason() ?: FinishReason.STOP, usage))
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
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyText = try {
                response.bodyOrThrow()
            } catch (e: ProviderHttpException) {
                throw e.toReadableException()
            }

            val parsed = ProviderJson.decodeFromString(GeminiModelsResponse.serializer(), bodyText)
            parsed.models
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
    }

    override suspend fun testConnection(): Result<Unit> = listModels().map { }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

private fun String.toFinishReason(): FinishReason = when (this) {
    "STOP" -> FinishReason.STOP
    "MAX_TOKENS" -> FinishReason.LENGTH
    "SAFETY", "RECITATION" -> FinishReason.CONTENT_FILTER
    else -> FinishReason.STOP
}

private fun ProviderHttpException.toReadableException(): Exception {
    val parsedMessage = runCatching {
        ProviderJson.decodeFromString(GeminiErrorResponse.serializer(), message.orEmpty()).error?.message
    }.getOrNull()
    return Exception(parsedMessage?.takeIf { it.isNotBlank() } ?: message, this)
}
