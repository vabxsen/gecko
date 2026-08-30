package com.orca.core.data.mapper

import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.chat.MessageRole
import com.orca.core.model.chat.MessageStatus
import com.orca.core.model.chat.TokenUsage
import com.orca.core.model.conversation.Conversation
import com.orca.core.model.provider.ProviderId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationMappersTest {

    @Test
    fun conversationRoundTripsThroughEntity() {
        val conversation = Conversation(
            id = "c1",
            title = "Trip planning",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(2_000),
            pinned = true,
            providerId = ProviderId.ANTHROPIC,
            modelId = "claude-3-5-sonnet",
        )

        val roundTripped = conversation.toEntity().toDomain()

        assertEquals(conversation, roundTripped)
    }

    @Test
    fun messageWithUsageRoundTripsThroughEntity() {
        val message = ChatMessage(
            id = "m1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "Hello there",
            createdAt = Instant.ofEpochMilli(5_000),
            status = MessageStatus.COMPLETE,
            providerId = ProviderId.OPENAI,
            modelId = "gpt-4o",
            tokenUsage = TokenUsage(10, 5, 15),
            errorMessage = null,
        )

        val roundTripped = message.toEntity().toDomain()

        assertEquals(message, roundTripped)
    }

    @Test
    fun messageWithoutUsageMapsNullTokenUsage() {
        val message = ChatMessage(
            id = "m2",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "Hi",
            createdAt = Instant.EPOCH,
            status = MessageStatus.COMPLETE,
        )

        val entity = message.toEntity()

        assertNull(entity.promptTokens)
        assertNull(entity.completionTokens)
        assertNull(entity.totalTokens)
        assertNull(entity.toDomain().tokenUsage)
    }
}
