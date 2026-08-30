package com.gecko.feature.chat

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeConversationRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.EditAndResendMessageUseCase
import com.gecko.domain.usecase.RegenerateResponseUseCase
import com.gecko.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildViewModel(
        conversationRepository: FakeConversationRepository = FakeConversationRepository(),
        providerConfigRepository: FakeProviderConfigRepository = FakeProviderConfigRepository(),
        chatCompletionRepository: FakeChatCompletionRepository = FakeChatCompletionRepository(),
        defaultProviderId: ProviderId? = ProviderId.OPENAI,
        defaultModelId: String? = "gpt-4o",
    ): ChatViewModel {
        val userPreferencesRepository = FakeUserPreferencesRepository(
            UserPreferences(defaultProviderId = defaultProviderId, defaultModelId = defaultModelId),
        )
        return ChatViewModel(
            conversationRepository = conversationRepository,
            providerConfigRepository = providerConfigRepository,
            userPreferencesRepository = userPreferencesRepository,
            sendChatMessageUseCase = SendChatMessageUseCase(conversationRepository, chatCompletionRepository),
            regenerateResponseUseCase = RegenerateResponseUseCase(
                conversationRepository,
                SendChatMessageUseCase(conversationRepository, chatCompletionRepository),
            ),
            editAndResendMessageUseCase = EditAndResendMessageUseCase(
                conversationRepository,
                SendChatMessageUseCase(conversationRepository, chatCompletionRepository),
            ),
        )
    }

    @Test
    fun sendingFirstMessageCreatesConversationAndPersistsBothTurns() = runTest {
        val conversationRepository = FakeConversationRepository()
        val chatCompletionRepository = FakeChatCompletionRepository(
            flowBuilder = { flow { emit(ChatEvent.Started()); emit(ChatEvent.ContentDelta("Hi there")); emit(ChatEvent.Completed(FinishReason.STOP, null)) } },
        )
        val viewModel = buildViewModel(conversationRepository = conversationRepository, chatCompletionRepository = chatCompletionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.conversations.size)
        assertEquals(2, state.messages.size)
        assertEquals(MessageRole.USER, state.messages[0].role)
        assertEquals("Hello", state.messages[0].content)
        assertEquals(MessageRole.ASSISTANT, state.messages[1].role)
        assertEquals("Hi there", state.messages[1].content)
        assertEquals(MessageStatus.COMPLETE, state.messages[1].status)
        assertEquals(false, state.isGenerating)
    }

    @Test
    fun sendingWithoutProviderSelectedIsANoop() = runTest {
        val conversationRepository = FakeConversationRepository()
        val viewModel = buildViewModel(
            conversationRepository = conversationRepository,
            defaultProviderId = null,
            defaultModelId = null,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.conversations.size)
    }

    @Test
    fun stoppingGenerationMarksMessageStopped() = runTest {
        val conversationRepository = FakeConversationRepository()
        val chatCompletionRepository = FakeChatCompletionRepository(
            flowBuilder = {
                flow {
                    emit(ChatEvent.Started())
                    emit(ChatEvent.ContentDelta("Partial"))
                    kotlinx.coroutines.delay(Long.MAX_VALUE / 2)
                }
            },
        )
        val viewModel = buildViewModel(conversationRepository = conversationRepository, chatCompletionRepository = chatCompletionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        runCurrent()
        viewModel.stopGeneration()
        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals(MessageStatus.STOPPED, assistant.status)
        assertEquals("Partial", assistant.content)
    }

    @Test
    fun errorEventSurfacesAsUiStateErrorMessage() = runTest {
        val chatCompletionRepository = FakeChatCompletionRepository(
            flowBuilder = { flow { emit(ChatEvent.Started()); emit(ChatEvent.Error("boom", null, true)) } },
        )
        val viewModel = buildViewModel(chatCompletionRepository = chatCompletionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.errorMessage)

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun regenerateReplacesLastAssistantMessage() = runTest {
        val conversationRepository = FakeConversationRepository()
        var replyCount = 0
        val chatCompletionRepository = FakeChatCompletionRepository(
            flowBuilder = {
                replyCount++
                flow { emit(ChatEvent.Started()); emit(ChatEvent.ContentDelta("Reply $replyCount")); emit(ChatEvent.Completed(FinishReason.STOP, null)) }
            },
        )
        val viewModel = buildViewModel(conversationRepository = conversationRepository, chatCompletionRepository = chatCompletionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        advanceUntilIdle()
        viewModel.regenerate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("Reply 2", state.messages[1].content)
    }

    @Test
    fun modelSelectorObservesEnabledProviders() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = buildViewModel(providerConfigRepository = providerConfigRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.providerConfigs.isEmpty()) state = awaitItem()
            assertEquals(0, state.enabledProviders.size)

            providerConfigRepository.setEnabled(ProviderId.OPENAI, true)
            providerConfigRepository.setHasApiKey(ProviderId.OPENAI, true)

            state = awaitItem()
            while (state.enabledProviders.isEmpty()) state = awaitItem()
            assertEquals(1, state.enabledProviders.size)
            assertEquals(ProviderId.OPENAI, state.enabledProviders.first().providerId)
        }
    }
}
