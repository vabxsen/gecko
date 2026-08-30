package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ConversationRepository
import com.gecko.domain.repository.SecureKeyRepository
import com.gecko.domain.repository.UserPreferencesRepository

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
