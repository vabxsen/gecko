package com.gecko.domain.model

import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ConversationHistoryTrimmerTest {

    private fun message(role: MessageRole, content: String, id: String = "m-${content.hashCode()}"): ChatMessage = ChatMessage(
        id = id,
        conversationId = "c1",
        role = role,
        content = content,
        createdAt = Instant.EPOCH,
        status = MessageStatus.COMPLETE,
    )

    @Test
    fun shortConversationIsUnchanged() {
        val history = listOf(
            message(MessageRole.SYSTEM, "Be terse"),
            message(MessageRole.USER, "Hi"),
            message(MessageRole.ASSISTANT, "Hello"),
        )

        assertEquals(history, history.trimToContextBudget(128_000))
    }

    @Test
    fun nullContextWindowLeavesHistoryUnchanged() {
        val history = listOf(message(MessageRole.USER, "x".repeat(1_000_000)))

        assertSame(history, history.trimToContextBudget(null))
    }

    @Test
    fun dropsOldestNonSystemMessagesFirstToFitBudget() {
        val system = message(MessageRole.SYSTEM, "Be terse")
        val oldUser = message(MessageRole.USER, "old ".repeat(2000), id = "old-user")
        val oldAssistant = message(MessageRole.ASSISTANT, "old reply ".repeat(2000), id = "old-assistant")
        val latestUser = message(MessageRole.USER, "What's the weather?", id = "latest-user")
        val history = listOf(system, oldUser, oldAssistant, latestUser)

        // Small enough window that the old exchange can't possibly fit alongside the reserve.
        val trimmed = history.trimToContextBudget(contextWindowTokens = 4_096)

        assertEquals(listOf(system, latestUser), trimmed)
    }

    @Test
    fun neverDropsSystemMessageOrMostRecentUserMessageEvenIfStillOverBudget() {
        val system = message(MessageRole.SYSTEM, "s".repeat(1_000_000))
        val latestUser = message(MessageRole.USER, "u".repeat(1_000_000))
        val history = listOf(system, latestUser)

        val trimmed = history.trimToContextBudget(contextWindowTokens = 4_096)

        assertEquals(listOf(system, latestUser), trimmed)
    }
}
