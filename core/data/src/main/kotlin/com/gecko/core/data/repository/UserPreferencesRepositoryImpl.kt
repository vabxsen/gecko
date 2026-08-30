package com.gecko.core.data.repository

import com.gecko.core.datastore.UserPreferencesDataSource
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataSource.userPreferences

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)
    override suspend fun setDynamicColorEnabled(enabled: Boolean) = dataSource.setDynamicColorEnabled(enabled)
    override suspend fun setDefaultProvider(providerId: ProviderId?) = dataSource.setDefaultProvider(providerId)
    override suspend fun setDefaultModel(modelId: String?) = dataSource.setDefaultModel(modelId)
    override suspend fun setSendOnEnter(enabled: Boolean) = dataSource.setSendOnEnter(enabled)
    override suspend fun setStreamingEnabled(enabled: Boolean) = dataSource.setStreamingEnabled(enabled)
    override suspend fun setOnboardingCompleted(completed: Boolean) = dataSource.setOnboardingCompleted(completed)
    override suspend fun setLastOpenedConversationId(conversationId: String?) =
        dataSource.setLastOpenedConversationId(conversationId)

    override suspend fun clearAll() = dataSource.clearAll()
}
