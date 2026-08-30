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

        return chatCompletionRepository.sendMessage(configId, modelId, history, streaming)
            .onEach { event ->
                when (event) {
                    is ChatEvent.ContentDelta -> {
                        buffer.append(event.text)
                        // Persisted per-delta (not just at the end) so the UI's reactive
                        // observeMessages() Flow shows tokens arriving in real time.
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
                        ),
                    )
                    conversationRepository.touchConversation(conversationId)
                }
            }
    }
}
