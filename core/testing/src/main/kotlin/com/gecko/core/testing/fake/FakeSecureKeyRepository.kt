package com.gecko.core.testing.fake

import com.gecko.domain.repository.SecureKeyRepository

class FakeSecureKeyRepository : SecureKeyRepository {
    private val keys = mutableMapOf<String, String>()

    override suspend fun saveApiKey(id: String, key: String) {
        keys[id] = key
    }

    override suspend fun getApiKey(id: String): String? = keys[id]

    override suspend fun clearApiKey(id: String) {
        keys.remove(id)
    }

    override suspend fun hasApiKey(id: String): Boolean = keys.containsKey(id)
}
