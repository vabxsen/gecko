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

class RegenerateResponseUseCaseTest {

    @Test
    fun removesLastAssistantMessageBeforeResending() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.OPENAI, "gpt-4o")
        val userMessage = ChatMessage(
            id = "u1",
            conversationId = conversation.id,
            role = MessageRole.USER,
            content = "Hi",
            createdAt = Instant.EPOCH,
            status = MessageStatus.COMPLETE,
        )
        val oldAssistantMessage = userMessage.copy(id = "a1", role = MessageRole.ASSISTANT, content = "Old reply", createdAt = Instant.EPOCH.plusSeconds(1))
        conversationRepo.saveMessage(userMessage)
        conversationRepo.saveMessage(oldAssistantMessage)

        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = { flowOf(ChatEvent.Started(), ChatEvent.ContentDelta("New reply"), ChatEvent.Completed(FinishReason.STOP, null)) },
        )
        val useCase = RegenerateResponseUseCase(conversationRepo, SendChatMessageUseCase(conversationRepo, chatRepo))

        useCase(conversation.id, "config-1", ProviderId.OPENAI, "gpt-4o", streaming = true).collect { }

        val messages = conversationRepo.observeMessages(conversation.id).first()
        assertFalse(messages.any { it.id == "a1" })
        assertEquals("New reply", messages.single { it.role == MessageRole.ASSISTANT }.content)
        assertEquals(listOf(userMessage), chatRepo.lastRequest?.third)
    }
}
