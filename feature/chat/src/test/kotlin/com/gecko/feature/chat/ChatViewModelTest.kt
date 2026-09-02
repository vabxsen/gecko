package com.gecko.feature.chat

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeConversationRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.EditAndResendMessageUseCase
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
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

    private suspend fun buildViewModel(
        conversationRepository: FakeConversationRepository = FakeConversationRepository(),
        providerConfigRepository: FakeProviderConfigRepository = FakeProviderConfigRepository(),
        chatCompletionRepository: FakeChatCompletionRepository = FakeChatCompletionRepository(),
        defaultProviderId: ProviderId? = ProviderId.OPENAI,
        defaultModelId: String? = "gpt-4o",
    ): ChatViewModel {
        val defaultConfigId = defaultProviderId?.let {
            providerConfigRepository.addProvider(it, it.displayName).getOrThrow()
        }
        val userPreferencesRepository = FakeUserPreferencesRepository(
            UserPreferences(defaultProviderConfigId = defaultConfigId, defaultModelId = defaultModelId),
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
            refreshProviderModelsUseCase = RefreshProviderModelsUseCase(chatCompletionRepository, providerConfigRepository),
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
    fun errorEventSurfacesAsAnExplainableError() = runTest {
        val chatCompletionRepository = FakeChatCompletionRepository(
            flowBuilder = {
                flow { emit(ChatEvent.Started()); emit(ChatEvent.Error(GeckoError(ErrorKind.RateLimited, "boom"))) }
            },
        )
        val viewModel = buildViewModel(chatCompletionRepository = chatCompletionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        // The kind is what drives the dialog's copy and its fix button, so it's the part that
        // has to survive — the raw provider text is only supporting detail.
        assertEquals(ErrorKind.RateLimited, viewModel.uiState.value.error?.kind)
        assertEquals("boom", viewModel.uiState.value.error?.technicalDetail)

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
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
    fun sendingWhileAlreadyGeneratingIsANoop() = runTest {
        val conversationRepository = FakeConversationRepository()
        var invocationCount = 0
        val chatCompletionRepository = FakeChatCompletionRepository(
            flowBuilder = {
                invocationCount++
                flow {
                    emit(ChatEvent.Started())
                    kotlinx.coroutines.delay(Long.MAX_VALUE / 2)
                }
            },
        )
        val viewModel = buildViewModel(conversationRepository = conversationRepository, chatCompletionRepository = chatCompletionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.sendMessage("Hello")
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isGenerating)

        val messagesAfterFirstSend = viewModel.uiState.value.messages.size

        viewModel.sendMessage("Second, while still generating")
        viewModel.regenerate()
        runCurrent()

        assertEquals(1, invocationCount)
        assertEquals(messagesAfterFirstSend, viewModel.uiState.value.messages.size)
    }

    private fun geminiModel(id: String) = ModelInfo(
        providerId = ProviderId.GOOGLE,
        modelId = id,
        displayName = id,
        contextWindowTokens = 1_000_000,
        supportsStreaming = true,
        supportsImages = true,
    )

    /**
     * Adds an enabled, keyed Google config with [modelIds] already cached. `gemini-pro-latest` is
     * on the curated shortlist; anything else lands in the "Show all models" remainder.
     */
    private suspend fun FakeProviderConfigRepository.addGoogleKeyWith(vararg modelIds: String): String {
        val configId = addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        setHasApiKey(configId, true)
        saveModels(configId, modelIds.map(::geminiModel))
        return configId
    }

    @Test
    fun choosingAModelFromOutsideTheCuratedShortlistIsNotRevertedToTheDefault() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = buildViewModel(
            providerConfigRepository = providerConfigRepository,
            defaultProviderId = null,
            defaultModelId = null,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        val configId = providerConfigRepository.addGoogleKeyWith("gemini-pro-latest", "gemini-experimental-x")
        advanceUntilIdle()

        viewModel.selectModel(configId, "gemini-experimental-x")
        advanceUntilIdle()

        assertEquals(configId, viewModel.uiState.value.selectedConfigId)
        assertEquals("gemini-experimental-x", viewModel.uiState.value.selectedModelId)
    }

    @Test
    fun aModelTheProviderNoLongerOffersFallsBackToTheCuratedDefault() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = buildViewModel(
            providerConfigRepository = providerConfigRepository,
            defaultProviderId = null,
            defaultModelId = null,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        val configId = providerConfigRepository.addGoogleKeyWith("gemini-pro-latest", "gemini-experimental-x")
        advanceUntilIdle()

        viewModel.selectModel(configId, "gemini-experimental-x")
        advanceUntilIdle()
        // The provider retires that id on its next catalog refresh.
        providerConfigRepository.saveModels(configId, listOf(geminiModel("gemini-pro-latest")))
        advanceUntilIdle()

        assertEquals("gemini-pro-latest", viewModel.uiState.value.selectedModelId)
    }

    @Test
    fun aFirstProviderWithNothingSelectedGetsAdoptedAutomatically() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = buildViewModel(
            providerConfigRepository = providerConfigRepository,
            defaultProviderId = null,
            defaultModelId = null,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedModelId)

        val configId = providerConfigRepository.addGoogleKeyWith("gemini-pro-latest", "gemini-experimental-x")
        advanceUntilIdle()

        assertEquals(configId, viewModel.uiState.value.selectedConfigId)
        assertEquals("gemini-pro-latest", viewModel.uiState.value.selectedModelId)
        assertEquals(true, viewModel.uiState.value.canSend)
    }

    @Test
    fun everyEnabledKeysCatalogIsExposedAtOnceNotJustTheSelectedOne() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = buildViewModel(
            providerConfigRepository = providerConfigRepository,
            defaultProviderId = null,
            defaultModelId = null,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        val googleId = providerConfigRepository.addGoogleKeyWith("gemini-pro-latest")
        val openAiId = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        providerConfigRepository.setHasApiKey(openAiId, true)
        providerConfigRepository.saveModels(
            openAiId,
            listOf(geminiModel("gpt-4o").copy(providerId = ProviderId.OPENAI)),
        )
        advanceUntilIdle()

        val catalog = viewModel.uiState.value.modelCatalog
        assertEquals(listOf("gemini-pro-latest"), catalog[googleId]?.map { it.modelId })
        assertEquals(listOf("gpt-4o"), catalog[openAiId]?.map { it.modelId })
    }

    @Test
    fun aKeyWithNoCachedCatalogIsFetchedInTheBackground() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val chatCompletionRepository = FakeChatCompletionRepository(
            fetchModelsResult = Result.success(listOf(geminiModel("gemini-pro-latest"))),
        )
        val viewModel = buildViewModel(
            providerConfigRepository = providerConfigRepository,
            chatCompletionRepository = chatCompletionRepository,
            defaultProviderId = null,
            defaultModelId = null,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        val configId = providerConfigRepository.addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        providerConfigRepository.setHasApiKey(configId, true)
        advanceUntilIdle()

        assertEquals(
            listOf("gemini-pro-latest"),
            viewModel.uiState.value.modelCatalog[configId]?.map { it.modelId },
        )
        assertEquals(emptySet<String>(), viewModel.uiState.value.loadingModelConfigIds)
    }

    @Test
    fun modelSelectorObservesEnabledProviders() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = buildViewModel(providerConfigRepository = providerConfigRepository, defaultProviderId = null)

        viewModel.uiState.test {
            var state = awaitItem()
            assertEquals(0, state.enabledProviders.size)

            val configId = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
            providerConfigRepository.setEnabled(configId, true)
            providerConfigRepository.setHasApiKey(configId, true)

            state = awaitItem()
            while (state.enabledProviders.isEmpty()) state = awaitItem()
            assertEquals(1, state.enabledProviders.size)
            assertEquals(ProviderId.OPENAI, state.enabledProviders.first().providerId)
        }
    }
}
