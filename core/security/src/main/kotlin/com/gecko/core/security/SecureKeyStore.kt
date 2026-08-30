package com.gecko.core.security

/**
 * Encrypted, on-device storage for provider API keys, keyed by the caller-assigned config id
 * (not the provider type — a single provider type can have multiple saved keys).
 * Implementations must never persist plaintext keys, log key material, or surface it outside
 * this interface.
 */
interface SecureKeyStore {
    suspend fun saveApiKey(id: String, key: String)
    suspend fun getApiKey(id: String): String?
    suspend fun clearApiKey(id: String)
    suspend fun hasApiKey(id: String): Boolean
}
