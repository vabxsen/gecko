package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.common.util.newId
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ConversationRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Streams an assistant reply for [history] and persists the result. A placeholder assistant
 * message is written immediately so it survives process death; the final content/status is
 * written once the stream completes, errors, or is cancelled ("stop generation").
 */
class SendChatMessageUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
) {
    suspend operator fun invoke(
        conversationId: String,
        configId: String,
        providerId: ProviderId,
        modelId: String,
        history: List<ChatMessage>,
        streaming: Boolean,
    ): Flow<ChatEvent> {
        val assistantMessageId = newId()
        val createdAt = Instant.now()

        conversationRepository.saveMessage(
            ChatMessage(
                id = assistantMessageId,
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                createdAt = createdAt,
                status = MessageStatus.STREAMING,
                providerId = providerId,
                modelId = modelId,
            ),
        )

        val buffer = StringBuilder()
        var usage: TokenUsage? = null
        var errorMessage: String? = null
        var generatedImageBase64: String? = null
        var lastPersistedAtMs = 0L

        return chatCompletionRepository.sendMessage(configId, modelId, history, streaming)
            .onEach { event ->
                when (event) {
                    is ChatEvent.ContentDelta -> {
                        val wasEmpty = buffer.isEmpty()
                        buffer.append(event.text)
                        // Persisted so the UI's reactive observeMessages() Flow shows tokens
                        // arriving in real time, but throttled: a DB write per token causes a
                        // full Room re-query + list recomposition + markdown re-parse of the
                        // whole (growing) message on every single token, which gets visibly
                        // slow on longer replies. The first delta always flushes immediately
                        // so the reply doesn't feel delayed; onCompletion always flushes the
                        // final content regardless of this throttle.
                        val now = System.currentTimeMillis()
                        if (wasEmpty || now - lastPersistedAtMs >= STREAM_PERSIST_INTERVAL_MS) {
                            lastPersistedAtMs = now
                            conversationRepository.saveMessage(
                                ChatMessage(
                                    id = assistantMessageId,
                                    conversationId = conversationId,
                                    role = MessageRole.ASSISTANT,
                                    content = buffer.toString(),
                                    createdAt = createdAt,
                                    status = MessageStatus.STREAMING,
                                    providerId = providerId,
                                    modelId = modelId,
                                    generatedImageBase64 = generatedImageBase64,
                                ),
                            )
                        }
                    }
                    is ChatEvent.ImageDelta -> {
                        // Arrives as one whole chunk, not token-by-token, and a response can be
                        // image-only with no text at all — flush immediately rather than waiting
                        // on the text throttle window above.
                        generatedImageBase64 = event.base64
                        conversationRepository.saveMessage(
                            ChatMessage(
                                id = assistantMessageId,
                                conversationId = conversationId,
                                role = MessageRole.ASSISTANT,
                                content = buffer.toString(),
                                createdAt = createdAt,
                                status = MessageStatus.STREAMING,
                                providerId = providerId,
                                modelId = modelId,
                                generatedImageBase64 = generatedImageBase64,
                            ),
                        )
                    }
                    is ChatEvent.Completed -> usage = event.usage
                    is ChatEvent.Error -> errorMessage = event.message
                    is ChatEvent.Started -> Unit
                }
            }
            .onCompletion { cause ->
                withContext(NonCancellable) {
                    val status = when {
                        cause is CancellationException -> MessageStatus.STOPPED
                        cause != null || errorMessage != null -> MessageStatus.ERROR
                        else -> MessageStatus.COMPLETE
                    }
                    conversationRepository.saveMessage(
                        ChatMessage(
                            id = assistantMessageId,
                            conversationId = conversationId,
                            role = MessageRole.ASSISTANT,
                            content = buffer.toString(),
                            createdAt = createdAt,
                            status = status,
                            providerId = providerId,
                            modelId = modelId,
                            tokenUsage = usage,
                            errorMessage = errorMessage ?: cause?.takeIf { it !is CancellationException }?.message,
                            generatedImageBase64 = generatedImageBase64,
                        ),
                    )
                    conversationRepository.touchConversation(conversationId)
                }
            }
    }

    private companion object {
        const val STREAM_PERSIST_INTERVAL_MS = 120L
    }
}
