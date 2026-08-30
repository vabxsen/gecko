package com.gecko.core.testing.fake

import com.gecko.core.common.util.newId
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.conversation.Conversation
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ConversationRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeConversationRepository : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messagesByConversation = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    override fun observeConversations(): Flow<List<Conversation>> = conversations

    override fun searchConversations(query: String): Flow<List<Conversation>> =
        conversations.map { list -> list.filter { it.title.contains(query, ignoreCase = true) } }

    override suspend fun getConversation(id: String): Conversation? = conversations.value.find { it.id == id }

    override suspend fun createConversation(providerId: ProviderId?, modelId: String?): Conversation {
        val now = Instant.now()
        val conversation = Conversation(
            id = newId(),
            title = "New chat",
            createdAt = now,
            updatedAt = now,
            pinned = false,
            providerId = providerId,
            modelId = modelId,
        )
        conversations.update { it + conversation }
        messagesByConversation[conversation.id] = MutableStateFlow(emptyList())
        return conversation
    }

    override suspend fun renameConversation(id: String, title: String) {
        conversations.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        conversations.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    override suspend fun touchConversation(id: String) {
        conversations.update { list -> list.map { if (it.id == id) it.copy(updatedAt = Instant.now()) else it } }
    }

    override suspend fun deleteConversation(id: String) {
        conversations.update { list -> list.filterNot { it.id == id } }
        messagesByConversation.remove(id)
    }

    override suspend fun deleteAllConversations() {
        conversations.value = emptyList()
        messagesByConversation.clear()
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messagesByConversation.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun saveMessage(message: ChatMessage) {
        val flow = messagesByConversation.getOrPut(message.conversationId) { MutableStateFlow(emptyList()) }
        flow.update { list ->
            if (list.any { it.id == message.id }) {
                list.map { if (it.id == message.id) message else it }
            } else {
                list + message
            }
        }
    }

    override suspend fun deleteMessage(id: String) {
        messagesByConversation.values.forEach { flow -> flow.update { list -> list.filterNot { it.id == id } } }
    }

    override suspend fun deleteMessagesAfter(conversationId: String, after: Instant) {
        messagesByConversation[conversationId]?.update { list -> list.filter { !it.createdAt.isAfter(after) } }
    }
}
