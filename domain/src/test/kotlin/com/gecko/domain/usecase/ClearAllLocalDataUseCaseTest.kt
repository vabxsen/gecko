package com.gecko.domain.usecase

import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.usecase.fakes.FakeConversationRepository
import com.gecko.domain.usecase.fakes.FakeSecureKeyRepository
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
