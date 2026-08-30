package com.gecko.domain.usecase

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.usecase.fakes.FakeChatCompletionRepository
import com.gecko.domain.usecase.fakes.FakeConversationRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditAndResendMessageUseCaseTest {

    @Test
    fun truncatesEverythingAfterTheEditedMessage() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.OPENAI, "gpt-4o")
        val firstUser = ChatMessage("u1", conversation.id, MessageRole.USER, "First", Instant.EPOCH, MessageStatus.COMPLETE)
        val firstAssistant = firstUser.copy(id = "a1", role = MessageRole.ASSISTANT, content = "Reply1", createdAt = Instant.EPOCH.plusSeconds(1))
        val secondUser = firstUser.copy(id = "u2", content = "Second", createdAt = Instant.EPOCH.plusSeconds(2))
        listOf(firstUser, firstAssistant, secondUser).forEach { conversationRepo.saveMessage(it) }

        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = { flowOf(ChatEvent.Started(), ChatEvent.ContentDelta("Reply2"), ChatEvent.Completed(FinishReason.STOP, null)) },
        )
        val useCase = EditAndResendMessageUseCase(conversationRepo, SendChatMessageUseCase(conversationRepo, chatRepo))

        useCase(conversation.id, "u1", "First edited", ProviderId.OPENAI, "gpt-4o", streaming = true).collect { }

        val messages = conversationRepo.observeMessages(conversation.id).first()
        assertFalse(messages.any { it.id == "a1" })
        assertFalse(messages.any { it.id == "u2" })
        assertEquals("First edited", messages.first { it.id == "u1" }.content)
        assertEquals("Reply2", messages.single { it.role == MessageRole.ASSISTANT }.content)
    }
}
