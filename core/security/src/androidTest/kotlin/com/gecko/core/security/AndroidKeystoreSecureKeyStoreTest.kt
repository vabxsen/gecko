package com.gecko.core.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gecko.core.model.provider.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecureKeyStoreTest {

    private lateinit var context: Context
    private lateinit var store: SecureKeyStore

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = AndroidKeystoreSecureKeyStore(context)
        ProviderId.entries.forEach { store.clearApiKey(it) }
    }

    @Test
    fun savedKeyCanBeRetrieved() = runBlocking {
        store.saveApiKey(ProviderId.OPENAI, "sk-test-12345")

        assertEquals("sk-test-12345", store.getApiKey(ProviderId.OPENAI))
    }

    @Test
    fun missingKeyReturnsNull() = runBlocking {
        assertNull(store.getApiKey(ProviderId.OPENAI))
    }

    @Test
    fun hasApiKeyReflectsPresence() = runBlocking {
        assertFalse(store.hasApiKey(ProviderId.ANTHROPIC))

        store.saveApiKey(ProviderId.ANTHROPIC, "key")

        assertTrue(store.hasApiKey(ProviderId.ANTHROPIC))
    }

    @Test
    fun clearRemovesKey() = runBlocking {
        store.saveApiKey(ProviderId.GOOGLE, "key")

        store.clearApiKey(ProviderId.GOOGLE)

        assertNull(store.getApiKey(ProviderId.GOOGLE))
        assertFalse(store.hasApiKey(ProviderId.GOOGLE))
    }

    @Test
    fun differentProvidersAreIsolated() = runBlocking {
        store.saveApiKey(ProviderId.OPENAI, "openai-key")
        store.saveApiKey(ProviderId.OPENROUTER, "openrouter-key")

        assertEquals("openai-key", store.getApiKey(ProviderId.OPENAI))
        assertEquals("openrouter-key", store.getApiKey(ProviderId.OPENROUTER))
    }

    @Test
    fun overwritingAKeyReplacesThePreviousValue() = runBlocking {
        store.saveApiKey(ProviderId.OPENAI, "first-value")
        store.saveApiKey(ProviderId.OPENAI, "second-value")

        assertEquals("second-value", store.getApiKey(ProviderId.OPENAI))
    }

    @Test
    fun persistedValueIsNotPlaintext() = runBlocking {
        val secret = "super-secret-plaintext-value"
        store.saveApiKey(ProviderId.OPENAI, secret)

        val prefs = context.getSharedPreferences("gecko_secure_prefs", Context.MODE_PRIVATE)
        val stored = prefs.getString("api_key_openai", null)

        assertNotNull(stored)
        assertFalse(stored!!.contains(secret))
    }
}
