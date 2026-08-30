package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Edits a previously sent user message, truncates everything after it, and asks for a new
 * reply. This is a linear edit (like most third-party clients) rather than branching history.
 */
class EditAndResendMessageUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
) {
    suspend operator fun invoke(
        conversationId: String,
        messageId: String,
        newContent: String,
        providerId: ProviderId,
        modelId: String,
        streaming: Boolean,
    ): Flow<ChatEvent> {
        val messages = conversationRepository.observeMessages(conversationId).first()
        val target = messages.first { it.id == messageId }
        val updated = target.copy(content = newContent)

        conversationRepository.saveMessage(updated)
        conversationRepository.deleteMessagesAfter(conversationId, target.createdAt)

        val history = messages.takeWhile { it.id != messageId } + updated
        return sendChatMessageUseCase(conversationId, providerId, modelId, history, streaming)
    }
}
