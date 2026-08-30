package com.orca.domain.usecase

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.chat.FinishReason
import com.orca.core.model.chat.MessageRole
import com.orca.core.model.chat.MessageStatus
import com.orca.core.model.provider.ProviderId
import com.orca.domain.usecase.fakes.FakeChatCompletionRepository
import com.orca.domain.usecase.fakes.FakeConversationRepository
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

        useCase(conversation.id, ProviderId.OPENAI, "gpt-4o", streaming = true).collect { }

        val messages = conversationRepo.observeMessages(conversation.id).first()
        assertFalse(messages.any { it.id == "a1" })
        assertEquals("New reply", messages.single { it.role == MessageRole.ASSISTANT }.content)
        assertEquals(listOf(userMessage), chatRepo.lastRequest?.third)
    }
}
