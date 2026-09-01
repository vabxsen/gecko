package com.gecko.core.provider.openai

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.network.sse.SseException
import com.gecko.core.network.sse.streamSse
import com.gecko.core.provider.internal.ProviderHttpException
import com.gecko.core.provider.internal.ProviderJson
import com.gecko.core.provider.internal.RetryPolicy
import com.gecko.core.provider.internal.bodyOrThrow
import com.gecko.core.provider.internal.executeWithRetry
import com.gecko.core.provider.internal.friendlyMessageFor
import com.gecko.core.provider.internal.httpStatusCodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Chat-completions request/streaming logic shared by every OpenAI-wire-format-compatible
 * provider (OpenAI itself, and OpenRouter). Only the base URL, auth headers, and model
 * catalog differ between them.
 */
internal class OpenAiChatEngine(
    private val httpClient: OkHttpClient,
    private val chatCompletionsUrl: String,
    private val applyHeaders: Request.Builder.() -> Unit,
) {
    fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent> = flow {
        emit(ChatEvent.Started())

        val requestBody = OpenAiChatRequest(
            model = model,
            messages = messages.map { it.toOpenAiMessage() },
            stream = stream,
            streamOptions = if (stream) OpenAiStreamOptions(includeUsage = true) else null,
        )
        val request = Request.Builder()
            .url(chatCompletionsUrl)
            .apply(applyHeaders)
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
                        if (event.data == "[DONE]") return@collect
                        val chunk = runCatching { ProviderJson.decodeFromString(OpenAiStreamChunk.serializer(), event.data) }
                            .getOrNull() ?: return@collect

                        chunk.choices.firstOrNull()?.let { choice ->
                            choice.delta.content?.let { text ->
                                if (text.isNotEmpty()) {
                                    contentReceived = true
                                    emit(ChatEvent.ContentDelta(text))
                                }
                            }
                            choice.finishReason?.let { finishReason = it.toFinishReason() }
                        }
                        chunk.usage?.let {
                            usage = TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens)
                        }
                    }
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
            val parsed = ProviderJson.decodeFromString(OpenAiChatResponse.serializer(), bodyText)
            val choice = parsed.choices.firstOrNull()
            choice?.message?.content?.let { text -> if (text.isNotEmpty()) emit(ChatEvent.ContentDelta(text)) }
            val usage = parsed.usage?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) }
            emit(ChatEvent.Completed(choice?.finishReason?.toFinishReason() ?: FinishReason.STOP, usage))
        }
    }.catchAsChatEvent()
}

private fun MessageRole.toWireRole(): String = when (this) {
    MessageRole.USER -> "user"
    MessageRole.ASSISTANT -> "assistant"
    MessageRole.SYSTEM -> "system"
}

private fun ChatMessage.toOpenAiMessage(): OpenAiMessage {
    val attachment = attachmentImageBase64
    val messageText = content
    val requestContent = if (attachment == null) {
        JsonPrimitive(messageText)
    } else {
        JsonArray(
            buildList {
                if (messageText.isNotEmpty()) {
                    add(JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive(messageText))))
                }
                add(
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("image_url"),
                            "image_url" to JsonObject(mapOf("url" to JsonPrimitive("data:image/jpeg;base64,$attachment"))),
                        ),
                    ),
                )
            },
        )
    }
    return OpenAiMessage(role = role.toWireRole(), content = requestContent)
}

private fun String.toFinishReason(): FinishReason = when (this) {
    "stop" -> FinishReason.STOP
    "length" -> FinishReason.LENGTH
    "content_filter" -> FinishReason.CONTENT_FILTER
    else -> FinishReason.STOP
}

internal fun ProviderHttpException.toReadableException(): Exception {
    val parsedMessage = runCatching {
        ProviderJson.decodeFromString(OpenAiErrorResponse.serializer(), message.orEmpty()).error?.message
    }.getOrNull()
    return Exception(parsedMessage?.takeIf { it.isNotBlank() } ?: message, this)
}

private fun Flow<ChatEvent>.catchAsChatEvent(): Flow<ChatEvent> = catch { e ->
    val rawMessage = when (e) {
        is SseException -> e.message ?: "Stream failed"
        else -> e.message ?: "Request failed"
    }
    val statusCode = e.httpStatusCodeOrNull()
    emit(
        ChatEvent.Error(
            message = friendlyMessageFor(statusCode, rawMessage),
            cause = e,
            isRetryable = RetryPolicy.isRetryableThrowable(e),
            httpStatusCode = statusCode,
        ),
    )
}
