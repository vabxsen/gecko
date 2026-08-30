package com.gecko.domain.usecase

import javax.inject.Inject

import com.gecko.domain.repository.ConversationRepository
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository

/** The "nuclear option": wipes conversations, provider API keys, and all preferences. */
class ClearAllLocalDataUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        conversationRepository.deleteAllConversations()
        providerConfigRepository.clearAll()
        userPreferencesRepository.clearAll()
    }
}
