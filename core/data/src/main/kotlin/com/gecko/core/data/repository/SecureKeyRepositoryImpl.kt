package com.gecko.core.data.repository

import com.gecko.core.model.provider.ProviderId
import com.gecko.core.security.SecureKeyStore
import com.gecko.domain.repository.SecureKeyRepository
import javax.inject.Inject

class SecureKeyRepositoryImpl @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
) : SecureKeyRepository {
    override suspend fun saveApiKey(providerId: ProviderId, key: String) = secureKeyStore.saveApiKey(providerId, key)
    override suspend fun getApiKey(providerId: ProviderId): String? = secureKeyStore.getApiKey(providerId)
    override suspend fun clearApiKey(providerId: ProviderId) = secureKeyStore.clearApiKey(providerId)
    override suspend fun hasApiKey(providerId: ProviderId): Boolean = secureKeyStore.hasApiKey(providerId)
}
