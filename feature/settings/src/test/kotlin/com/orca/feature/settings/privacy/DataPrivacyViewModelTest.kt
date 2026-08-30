package com.orca.feature.settings.privacy

import com.orca.core.model.provider.ProviderId
import com.orca.core.testing.fake.FakeConversationRepository
import com.orca.core.testing.fake.FakeSecureKeyRepository
import com.orca.core.testing.fake.FakeUserPreferencesRepository
import com.orca.core.testing.rule.MainDispatcherRule
import com.orca.domain.usecase.ClearAllLocalDataUseCase
import com.orca.domain.usecase.ExportConversationsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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
            ClearAllLocalDataUseCase(conversationRepository, FakeSecureKeyRepository(), FakeUserPreferencesRepository()),
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
            ClearAllLocalDataUseCase(conversationRepository, FakeSecureKeyRepository(), FakeUserPreferencesRepository()),
        )

        viewModel.deleteAllConversations()
        advanceUntilIdle()

        assertTrue(conversationRepository.observeConversations().first().isEmpty())
        assertTrue(viewModel.actionMessage.value != null)
    }

    @Test
    fun clearAllLocalDataClearsKeysToo() = runTest {
        val conversationRepository = FakeConversationRepository()
        val secureKeyRepository = FakeSecureKeyRepository()
        secureKeyRepository.saveApiKey(ProviderId.OPENAI, "sk-test")
        val viewModel = DataPrivacyViewModel(
            conversationRepository,
            ExportConversationsUseCase(conversationRepository),
            ClearAllLocalDataUseCase(conversationRepository, secureKeyRepository, FakeUserPreferencesRepository()),
        )

        viewModel.clearAllLocalData()
        advanceUntilIdle()

        assertFalse(secureKeyRepository.hasApiKey(ProviderId.OPENAI))
    }
}
