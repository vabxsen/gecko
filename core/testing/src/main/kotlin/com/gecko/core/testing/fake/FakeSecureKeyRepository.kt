package com.gecko.core.testing.fake

import com.gecko.domain.repository.SecureKeyRepository

class FakeSecureKeyRepository : SecureKeyRepository {
    private val keys = mutableMapOf<String, String>()

    /**
     * Reproduces a stored key the Android Keystore can no longer decrypt — after a system update
     * or a screen-lock change. The real store returns null there while the ciphertext is still
     * present, which is the exact state that used to be indistinguishable from having no key.
     */
    var simulateUndecryptable: Boolean = false

    override suspend fun saveApiKey(id: String, key: String) {
        keys[id] = key
    }

    override suspend fun getApiKey(id: String): String? = if (simulateUndecryptable) null else keys[id]

    override suspend fun clearApiKey(id: String) {
        keys.remove(id)
    }

    override suspend fun hasApiKey(id: String): Boolean = keys.containsKey(id)
}
