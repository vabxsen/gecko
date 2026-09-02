package com.gecko.domain.usecase

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.usecase.fakes.FakeChatCompletionRepository
import com.gecko.domain.usecase.fakes.FakeConversationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SendChatMessageUseCaseTest {

    @Test
    fun successfulStreamPersistsCompleteMessage() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.OPENAI, "gpt-4o")
        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = {
                flow {
                    emit(ChatEvent.Started())
                    emit(ChatEvent.ContentDelta("Hello"))
                    emit(ChatEvent.ContentDelta(" there"))
                    emit(ChatEvent.Completed(FinishReason.STOP, TokenUsage(10, 2, 12)))
                }
            },
        )
        val useCase = SendChatMessageUseCase(conversationRepo, chatRepo)

        useCase(conversation.id, "config-1", ProviderId.OPENAI, "gpt-4o", emptyList(), streaming = true).collect { }

        val assistant = conversationRepo.observeMessages(conversation.id).first().single { it.role == MessageRole.ASSISTANT }
        assertEquals(MessageStatus.COMPLETE, assistant.status)
        assertEquals("Hello there", assistant.content)
        assertEquals(TokenUsage(10, 2, 12), assistant.tokenUsage)
    }

    @Test
    fun imageDeltaPersistsGeneratedImageEvenWithNoText() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.GOOGLE, "gemini-2.5-flash-image")
        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = {
                flow {
                    emit(ChatEvent.Started())
                    emit(ChatEvent.ImageDelta(base64 = "iVBORw0KGgo=", mimeType = "image/png"))
                    emit(ChatEvent.Completed(FinishReason.STOP, null))
                }
            },
        )
        val useCase = SendChatMessageUseCase(conversationRepo, chatRepo)

        useCase(conversation.id, "config-1", ProviderId.GOOGLE, "gemini-2.5-flash-image", emptyList(), streaming = true).collect { }

        val assistant = conversationRepo.observeMessages(conversation.id).first().single { it.role == MessageRole.ASSISTANT }
        assertEquals(MessageStatus.COMPLETE, assistant.status)
        assertEquals("", assistant.content)
        assertEquals("iVBORw0KGgo=", assistant.generatedImageBase64)
    }

    @Test
    fun mixedTextAndImageDeltasPersistBoth() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.GOOGLE, "gemini-2.5-flash-image")
        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = {
                flow {
                    emit(ChatEvent.Started())
                    emit(ChatEvent.ContentDelta("Here you go:"))
                    emit(ChatEvent.ImageDelta(base64 = "AAAA", mimeType = "image/png"))
                    emit(ChatEvent.Completed(FinishReason.STOP, null))
                }
            },
        )
        val useCase = SendChatMessageUseCase(conversationRepo, chatRepo)

        useCase(conversation.id, "config-1", ProviderId.GOOGLE, "gemini-2.5-flash-image", emptyList(), streaming = true).collect { }

        val assistant = conversationRepo.observeMessages(conversation.id).first().single { it.role == MessageRole.ASSISTANT }
        assertEquals("Here you go:", assistant.content)
        assertEquals("AAAA", assistant.generatedImageBase64)
    }

    @Test
    fun errorEventPersistsErrorMessage() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.OPENAI, "gpt-4o")
        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = {
                flow {
                    emit(ChatEvent.Started())
                    emit(ChatEvent.ContentDelta("Partial"))
                    emit(ChatEvent.Error(GeckoError(ErrorKind.RateLimited, technicalDetail = "Rate limited")))
                }
            },
        )
        val useCase = SendChatMessageUseCase(conversationRepo, chatRepo)

        useCase(conversation.id, "config-1", ProviderId.OPENAI, "gpt-4o", emptyList(), streaming = true).collect { }

        val assistant = conversationRepo.observeMessages(conversation.id).first().single { it.role == MessageRole.ASSISTANT }
        assertEquals(MessageStatus.ERROR, assistant.status)
        assertEquals("Partial", assistant.content)
        assertEquals("Rate limited", assistant.errorMessage)
    }

    @Test
    fun cancellingMidStreamPersistsPartialContentAsStopped() = runTest {
        val conversationRepo = FakeConversationRepository()
        val conversation = conversationRepo.createConversation(ProviderId.OPENAI, "gpt-4o")
        val chatRepo = FakeChatCompletionRepository(
            flowBuilder = {
                flow {
                    emit(ChatEvent.Started())
                    emit(ChatEvent.ContentDelta("Hello"))
                    delay(Long.MAX_VALUE / 2)
                    emit(ChatEvent.ContentDelta(" never reached"))
                }
            },
        )
        val useCase = SendChatMessageUseCase(conversationRepo, chatRepo)
        val resultFlow = useCase(conversation.id, "config-1", ProviderId.OPENAI, "gpt-4o", emptyList(), streaming = true)

        val job = launch { resultFlow.collect { } }
        advanceTimeBy(1)
        runCurrent()
        job.cancel()
        job.join()

        val assistant = conversationRepo.observeMessages(conversation.id).first().single { it.role == MessageRole.ASSISTANT }
        assertEquals(MessageStatus.STOPPED, assistant.status)
        assertEquals("Hello", assistant.content)
        assertNotNull(conversationRepo.getConversation(conversation.id))
    }
}
