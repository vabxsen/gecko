package com.gecko.core.security

import com.gecko.core.model.provider.ProviderId

/**
 * Encrypted, on-device storage for provider API keys. Implementations must never persist
 * plaintext keys, log key material, or surface it outside this interface.
 */
interface SecureKeyStore {
    suspend fun saveApiKey(providerId: ProviderId, key: String)
    suspend fun getApiKey(providerId: ProviderId): String?
    suspend fun clearApiKey(providerId: ProviderId)
    suspend fun hasApiKey(providerId: ProviderId): Boolean
}
