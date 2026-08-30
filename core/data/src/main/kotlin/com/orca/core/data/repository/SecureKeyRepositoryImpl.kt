package com.orca.core.data.repository

import com.orca.core.model.provider.ProviderId
import com.orca.core.security.SecureKeyStore
import com.orca.domain.repository.SecureKeyRepository
import javax.inject.Inject

class SecureKeyRepositoryImpl @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
) : SecureKeyRepository {
    override suspend fun saveApiKey(providerId: ProviderId, key: String) = secureKeyStore.saveApiKey(providerId, key)
    override suspend fun getApiKey(providerId: ProviderId): String? = secureKeyStore.getApiKey(providerId)
    override suspend fun clearApiKey(providerId: ProviderId) = secureKeyStore.clearApiKey(providerId)
    override suspend fun hasApiKey(providerId: ProviderId): Boolean = secureKeyStore.hasApiKey(providerId)
}
