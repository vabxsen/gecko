package com.orca.domain.usecase

import javax.inject.Inject

import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ConversationRepository
import com.orca.domain.repository.SecureKeyRepository
import com.orca.domain.repository.UserPreferencesRepository

/** The "nuclear option": wipes conversations, provider API keys, and all preferences. */
class ClearAllLocalDataUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        conversationRepository.deleteAllConversations()
        ProviderId.entries.forEach { secureKeyRepository.clearApiKey(it) }
        userPreferencesRepository.clearAll()
    }
}
