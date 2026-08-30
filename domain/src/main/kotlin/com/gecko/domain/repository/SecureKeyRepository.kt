package com.gecko.domain.repository

/**
 * Deliberately separate from [ProviderConfigRepository] so key material never rides along
 * on the observable, frequently-recomposed provider config [kotlinx.coroutines.flow.Flow].
 * Keyed by the saved config's id, not the provider type — a provider type can have multiple
 * saved keys.
 */
interface SecureKeyRepository {
    suspend fun saveApiKey(id: String, key: String)
    suspend fun getApiKey(id: String): String?
    suspend fun clearApiKey(id: String)
    suspend fun hasApiKey(id: String): Boolean
}
