package com.orca.core.provider.openai

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.chat.FinishReason
import com.orca.core.model.chat.MessageRole
import com.orca.core.model.chat.TokenUsage
import com.orca.core.network.sse.SseException
import com.orca.core.network.sse.streamSse
import com.orca.core.provider.internal.ProviderHttpException
import com.orca.core.provider.internal.ProviderJson
import com.orca.core.provider.internal.bodyOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
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
            messages = messages.map { OpenAiMessage(role = it.role.toWireRole(), content = it.content) },
            stream = stream,
            streamOptions = if (stream) OpenAiStreamOptions(includeUsage = true) else null,
        )
        val request = Request.Builder()
            .url(chatCompletionsUrl)
            .apply(applyHeaders)
            .post(ProviderJson.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        if (stream) {
            var finishReason: FinishReason? = null
            var usage: TokenUsage? = null

            httpClient.streamSse(request).collect { event ->
                if (event.data == "[DONE]") return@collect
                val chunk = runCatching { ProviderJson.decodeFromString(OpenAiStreamChunk.serializer(), event.data) }
                    .getOrNull() ?: return@collect

                chunk.choices.firstOrNull()?.let { choice ->
                    choice.delta.content?.let { text -> if (text.isNotEmpty()) emit(ChatEvent.ContentDelta(text)) }
                    choice.finishReason?.let { finishReason = it.toFinishReason() }
                }
                chunk.usage?.let {
                    usage = TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens)
                }
            }

            emit(ChatEvent.Completed(finishReason ?: FinishReason.STOP, usage))
        } else {
            val response = httpClient.newCall(request).execute()
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
    val message = when (e) {
        is SseException -> e.message ?: "Stream failed"
        else -> e.message ?: "Request failed"
    }
    emit(ChatEvent.Error(message = message, cause = e, isRetryable = true))
}
