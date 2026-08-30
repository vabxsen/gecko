package com.gecko.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesDataSource(
    context: Context,
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(PREFS_NAME) },
    ),
) {
    val userPreferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[PreferencesKeys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[PreferencesKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            defaultProviderConfigId = prefs[PreferencesKeys.DEFAULT_PROVIDER_CONFIG_ID],
            defaultModelId = prefs[PreferencesKeys.DEFAULT_MODEL_ID],
            sendOnEnter = prefs[PreferencesKeys.SEND_ON_ENTER] ?: true,
            streamingEnabled = prefs[PreferencesKeys.STREAMING_ENABLED] ?: true,
            onboardingCompleted = prefs[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            lastOpenedConversationId = prefs[PreferencesKeys.LAST_OPENED_CONVERSATION_ID],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled }
    }

    suspend fun setDefaultProviderConfig(configId: String?) {
        dataStore.edit {
            if (configId == null) {
                it.remove(PreferencesKeys.DEFAULT_PROVIDER_CONFIG_ID)
            } else {
                it[PreferencesKeys.DEFAULT_PROVIDER_CONFIG_ID] = configId
            }
        }
    }

    suspend fun setDefaultModel(modelId: String?) {
        dataStore.edit {
            if (modelId == null) {
                it.remove(PreferencesKeys.DEFAULT_MODEL_ID)
            } else {
                it[PreferencesKeys.DEFAULT_MODEL_ID] = modelId
            }
        }
    }

    suspend fun setSendOnEnter(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SEND_ON_ENTER] = enabled }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.STREAMING_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLastOpenedConversationId(conversationId: String?) {
        dataStore.edit {
            if (conversationId == null) {
                it.remove(PreferencesKeys.LAST_OPENED_CONVERSATION_ID)
            } else {
                it[PreferencesKeys.LAST_OPENED_CONVERSATION_ID] = conversationId
            }
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        const val PREFS_NAME = "gecko_user_preferences"
    }
}
