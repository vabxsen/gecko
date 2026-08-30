package com.orca.domain.repository

import com.orca.core.model.preferences.ThemeMode
import com.orca.core.model.preferences.UserPreferences
import com.orca.core.model.provider.ProviderId
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
    suspend fun setDefaultProvider(providerId: ProviderId?)
    suspend fun setDefaultModel(modelId: String?)
    suspend fun setSendOnEnter(enabled: Boolean)
    suspend fun setStreamingEnabled(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setLastOpenedConversationId(conversationId: String?)
    suspend fun clearAll()
}
