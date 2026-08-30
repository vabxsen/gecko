package com.orca.domain.usecase

import com.orca.core.model.provider.ProviderId
import com.orca.domain.usecase.fakes.FakeConversationRepository
import com.orca.domain.usecase.fakes.FakeSecureKeyRepository
import com.orca.domain.usecase.fakes.FakeUserPreferencesRepository
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
        val keyRepo = FakeSecureKeyRepository()
        keyRepo.saveApiKey(ProviderId.OPENAI, "sk-test")
        val prefsRepo = FakeUserPreferencesRepository()
        prefsRepo.setOnboardingCompleted(true)

        val useCase = ClearAllLocalDataUseCase(conversationRepo, keyRepo, prefsRepo)
        useCase()

        assertTrue(conversationRepo.observeConversations().first().isEmpty())
        assertFalse(keyRepo.hasApiKey(ProviderId.OPENAI))
        assertFalse(prefsRepo.userPreferences.first().onboardingCompleted)
        assertTrue(prefsRepo.clearAllCalled)
    }
}
