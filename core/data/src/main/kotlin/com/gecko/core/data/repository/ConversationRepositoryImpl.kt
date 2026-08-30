package com.gecko.core.data.repository

import com.gecko.core.common.dispatchers.DispatcherProvider
import com.gecko.core.common.util.newId
import com.gecko.core.data.mapper.toDomain
import com.gecko.core.data.mapper.toEntity
import com.gecko.core.database.dao.ConversationDao
import com.gecko.core.database.dao.MessageDao
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.conversation.Conversation
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ConversationRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val dispatchers: DispatcherProvider,
) : ConversationRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun searchConversations(query: String): Flow<List<Conversation>> =
        conversationDao.search(query).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getConversation(id: String): Conversation? = withContext(dispatchers.io) {
        conversationDao.getById(id)?.toDomain()
    }

    override suspend fun createConversation(providerId: ProviderId?, modelId: String?): Conversation =
        withContext(dispatchers.io) {
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
            conversationDao.upsert(conversation.toEntity())
            conversation
        }

    override suspend fun renameConversation(id: String, title: String) = withContext(dispatchers.io) {
        conversationDao.rename(id, title, Instant.now().toEpochMilli())
    }

    override suspend fun setPinned(id: String, pinned: Boolean) = withContext(dispatchers.io) {
        conversationDao.setPinned(id, pinned, Instant.now().toEpochMilli())
    }

    override suspend fun touchConversation(id: String) = withContext(dispatchers.io) {
        conversationDao.touch(id, Instant.now().toEpochMilli())
    }

    override suspend fun deleteConversation(id: String) = withContext(dispatchers.io) {
        conversationDao.deleteById(id)
    }

    override suspend fun deleteAllConversations() = withContext(dispatchers.io) {
        conversationDao.deleteAll()
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(conversationId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveMessage(message: ChatMessage) = withContext(dispatchers.io) {
        messageDao.upsert(message.toEntity())
    }

    override suspend fun deleteMessage(id: String) = withContext(dispatchers.io) {
        messageDao.deleteById(id)
    }

    override suspend fun deleteMessagesAfter(conversationId: String, after: Instant) = withContext(dispatchers.io) {
        messageDao.deleteAfter(conversationId, after.toEpochMilli())
    }
}
