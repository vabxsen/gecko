package com.gecko.core.data.repository

import com.gecko.core.security.SecureKeyStore
import com.gecko.domain.repository.SecureKeyRepository
import javax.inject.Inject

class SecureKeyRepositoryImpl @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
) : SecureKeyRepository {
    override suspend fun saveApiKey(id: String, key: String) = secureKeyStore.saveApiKey(id, key)
    override suspend fun getApiKey(id: String): String? = secureKeyStore.getApiKey(id)
    override suspend fun clearApiKey(id: String) = secureKeyStore.clearApiKey(id)
    override suspend fun hasApiKey(id: String): Boolean = secureKeyStore.hasApiKey(id)
}
