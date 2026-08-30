package com.orca.domain.repository

import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.conversation.Conversation
import com.orca.core.model.provider.ProviderId
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun searchConversations(query: String): Flow<List<Conversation>>
    suspend fun getConversation(id: String): Conversation?
    suspend fun createConversation(providerId: ProviderId?, modelId: String?): Conversation
    suspend fun renameConversation(id: String, title: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun touchConversation(id: String)
    suspend fun deleteConversation(id: String)
    suspend fun deleteAllConversations()

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun saveMessage(message: ChatMessage)
    suspend fun deleteMessage(id: String)
    suspend fun deleteMessagesAfter(conversationId: String, after: Instant)
}
