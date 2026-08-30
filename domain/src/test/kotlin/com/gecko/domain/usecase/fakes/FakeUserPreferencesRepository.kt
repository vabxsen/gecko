package com.gecko.domain.usecase.fakes

import com.gecko.core.model.preferences.ThemeMode
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow(UserPreferences())
    override val userPreferences = state

    var clearAllCalled: Boolean = false
        private set

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        state.update { it.copy(dynamicColorEnabled = enabled) }
    }

    override suspend fun setDefaultProvider(providerId: ProviderId?) {
        state.update { it.copy(defaultProviderId = providerId) }
    }

    override suspend fun setDefaultModel(modelId: String?) {
        state.update { it.copy(defaultModelId = modelId) }
    }

    override suspend fun setSendOnEnter(enabled: Boolean) {
        state.update { it.copy(sendOnEnter = enabled) }
    }

    override suspend fun setStreamingEnabled(enabled: Boolean) {
        state.update { it.copy(streamingEnabled = enabled) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        state.update { it.copy(onboardingCompleted = completed) }
    }

    override suspend fun setLastOpenedConversationId(conversationId: String?) {
        state.update { it.copy(lastOpenedConversationId = conversationId) }
    }

    override suspend fun clearAll() {
        clearAllCalled = true
        state.value = UserPreferences()
    }
}
