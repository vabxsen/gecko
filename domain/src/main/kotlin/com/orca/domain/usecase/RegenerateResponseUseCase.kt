package com.orca.domain.usecase

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.MessageRole
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Discards the most recent assistant reply (if any) and asks for a new one. */
class RegenerateResponseUseCase(
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
) {
    suspend operator fun invoke(
        conversationId: String,
        providerId: ProviderId,
        modelId: String,
        streaming: Boolean,
    ): Flow<ChatEvent> {
        val messages = conversationRepository.observeMessages(conversationId).first()
        val lastAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val history = if (lastAssistant != null) {
            conversationRepository.deleteMessage(lastAssistant.id)
            messages.filterNot { it.id == lastAssistant.id }
        } else {
            messages
        }
        return sendChatMessageUseCase(conversationId, providerId, modelId, history, streaming)
    }
}
