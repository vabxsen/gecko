package com.orca.core.testing.fake

import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.SecureKeyRepository

class FakeSecureKeyRepository : SecureKeyRepository {
    private val keys = mutableMapOf<ProviderId, String>()

    override suspend fun saveApiKey(providerId: ProviderId, key: String) {
        keys[providerId] = key
    }

    override suspend fun getApiKey(providerId: ProviderId): String? = keys[providerId]

    override suspend fun clearApiKey(providerId: ProviderId) {
        keys.remove(providerId)
    }

    override suspend fun hasApiKey(providerId: ProviderId): Boolean = keys.containsKey(providerId)
}
