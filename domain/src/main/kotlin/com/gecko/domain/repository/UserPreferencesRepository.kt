package com.gecko.domain.repository

import com.gecko.core.model.preferences.ThemeMode
import com.gecko.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
    suspend fun setDefaultProviderConfig(configId: String?)
    suspend fun setDefaultModel(modelId: String?)
    suspend fun setSendOnEnter(enabled: Boolean)
    suspend fun setStreamingEnabled(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setLastOpenedConversationId(conversationId: String?)
    suspend fun clearAll()
}
