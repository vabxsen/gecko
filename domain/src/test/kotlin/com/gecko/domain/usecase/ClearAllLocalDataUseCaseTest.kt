package com.gecko.domain.usecase

import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.usecase.fakes.FakeConversationRepository
import com.gecko.domain.usecase.fakes.FakeProviderConfigRepository
import com.gecko.domain.usecase.fakes.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearAllLocalDataUseCaseTest {

    @Test
    fun wipesConversationsKeysAndPreferences() = runTest {
        val conversationRepo = FakeConversationRepository()
        conversationRepo.createConversation(ProviderId.OPENAI, "gpt-4o")
        val configRepo = FakeProviderConfigRepository()
        configRepo.addProvider(ProviderId.OPENAI, "OpenAI")
        val prefsRepo = FakeUserPreferencesRepository()
        prefsRepo.setOnboardingCompleted(true)

        val useCase = ClearAllLocalDataUseCase(conversationRepo, configRepo, prefsRepo)
        useCase()

        assertTrue(conversationRepo.observeConversations().first().isEmpty())
        assertTrue(configRepo.clearAllCalled)
        assertFalse(prefsRepo.userPreferences.first().onboardingCompleted)
        assertTrue(prefsRepo.clearAllCalled)
    }
}
