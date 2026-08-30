package com.orca.domain.repository

import com.orca.core.model.provider.ProviderId

/**
 * Deliberately separate from [ProviderConfigRepository] so key material never rides along
 * on the observable, frequently-recomposed provider config [kotlinx.coroutines.flow.Flow].
 */
interface SecureKeyRepository {
    suspend fun saveApiKey(providerId: ProviderId, key: String)
    suspend fun getApiKey(providerId: ProviderId): String?
    suspend fun clearApiKey(providerId: ProviderId)
    suspend fun hasApiKey(providerId: ProviderId): Boolean
}
