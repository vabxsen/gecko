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

    @Test
    fun corruptCiphertextIsReportedAsUnreadableWithoutCrashing() = runBlocking {
        context.getSharedPreferences("gecko_secure_prefs", Context.MODE_PRIVATE)
            .edit().putString("api_key_openai", "!invalid-base64!").commit()

        assertNull(store.getApiKey("openai"))
        assertTrue(store.hasApiKey("openai"))
    }

    private lateinit var context: Context
    private lateinit var store: SecureKeyStore

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = AndroidKeystoreSecureKeyStore(context)
        ProviderId.entries.forEach { store.clearApiKey(it.slug) }
    }

    @Test
    fun savedKeyCanBeRetrieved() = runBlocking {
        store.saveApiKey(ProviderId.OPENAI.slug, "sk-test-12345")

        assertEquals("sk-test-12345", store.getApiKey(ProviderId.OPENAI.slug))
    }

    @Test
    fun missingKeyReturnsNull() = runBlocking {
        assertNull(store.getApiKey(ProviderId.OPENAI.slug))
    }

    @Test
    fun hasApiKeyReflectsPresence() = runBlocking {
        assertFalse(store.hasApiKey(ProviderId.ANTHROPIC.slug))

        store.saveApiKey(ProviderId.ANTHROPIC.slug, "key")

        assertTrue(store.hasApiKey(ProviderId.ANTHROPIC.slug))
    }

    @Test
    fun clearRemovesKey() = runBlocking {
        store.saveApiKey(ProviderId.GOOGLE.slug, "key")

        store.clearApiKey(ProviderId.GOOGLE.slug)

        assertNull(store.getApiKey(ProviderId.GOOGLE.slug))
        assertFalse(store.hasApiKey(ProviderId.GOOGLE.slug))
    }

    @Test
    fun differentProvidersAreIsolated() = runBlocking {
        store.saveApiKey(ProviderId.OPENAI.slug, "openai-key")
        store.saveApiKey(ProviderId.OPENROUTER.slug, "openrouter-key")

        assertEquals("openai-key", store.getApiKey(ProviderId.OPENAI.slug))
        assertEquals("openrouter-key", store.getApiKey(ProviderId.OPENROUTER.slug))
    }

    @Test
    fun overwritingAKeyReplacesThePreviousValue() = runBlocking {
        store.saveApiKey(ProviderId.OPENAI.slug, "first-value")
        store.saveApiKey(ProviderId.OPENAI.slug, "second-value")

        assertEquals("second-value", store.getApiKey(ProviderId.OPENAI.slug))
    }

    @Test
    fun persistedValueIsNotPlaintext() = runBlocking {
        val secret = "super-secret-plaintext-value"
        store.saveApiKey(ProviderId.OPENAI.slug, secret)

        val prefs = context.getSharedPreferences("gecko_secure_prefs", Context.MODE_PRIVATE)
        val stored = prefs.getString("api_key_openai", null)

        assertNotNull(stored)
        assertFalse(stored!!.contains(secret))
    }
}
