package com.gecko.feature.settings.privacy

import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeConversationRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.ClearAllLocalDataUseCase
import com.gecko.domain.usecase.ExportConversationsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DataPrivacyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exportProducesMarkdownForExistingConversations() = runTest {
        val conversationRepository = FakeConversationRepository()
        conversationRepository.createConversation(ProviderId.OPENAI, "gpt-4o")
        val viewModel = DataPrivacyViewModel(
            conversationRepository,
            ExportConversationsUseCase(conversationRepository),
            ClearAllLocalDataUseCase(conversationRepository, FakeProviderConfigRepository(), FakeUserPreferencesRepository()),
        )

        viewModel.prepareExport()
        advanceUntilIdle()

        val markdown = viewModel.exportedMarkdown.value
        assertTrue(markdown != null && markdown.contains("New chat"))
    }

    @Test
    fun deleteAllConversationsEmptiesRepository() = runTest {
        val conversationRepository = FakeConversationRepository()
        conversationRepository.createConversation(ProviderId.OPENAI, "gpt-4o")
        val viewModel = DataPrivacyViewModel(
            conversationRepository,
            ExportConversationsUseCase(conversationRepository),
            ClearAllLocalDataUseCase(conversationRepository, FakeProviderConfigRepository(), FakeUserPreferencesRepository()),
        )

        viewModel.deleteAllConversations()
        advanceUntilIdle()

        assertTrue(conversationRepository.observeConversations().first().isEmpty())
        assertTrue(viewModel.actionMessage.value != null)
    }

    @Test
    fun clearAllLocalDataClearsKeysToo() = runTest {
        val conversationRepository = FakeConversationRepository()
        val providerConfigRepository = FakeProviderConfigRepository()
        providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI")
        val viewModel = DataPrivacyViewModel(
            conversationRepository,
            ExportConversationsUseCase(conversationRepository),
            ClearAllLocalDataUseCase(conversationRepository, providerConfigRepository, FakeUserPreferencesRepository()),
        )

        viewModel.clearAllLocalData()
        advanceUntilIdle()

        assertTrue(providerConfigRepository.clearAllCalled)
    }
}
