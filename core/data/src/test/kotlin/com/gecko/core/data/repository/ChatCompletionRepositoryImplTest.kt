package com.gecko.core.data.repository

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.provider.api.ProviderFactory
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeSecureKeyRepository
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatCompletionRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var secureKeyRepository: FakeSecureKeyRepository
    private lateinit var providerConfigRepository: FakeProviderConfigRepository
    private lateinit var repository: ChatCompletionRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        secureKeyRepository = FakeSecureKeyRepository()
        providerConfigRepository = FakeProviderConfigRepository()
        val httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        repository = ChatCompletionRepositoryImpl(secureKeyRepository, providerConfigRepository, ProviderFactory(httpClient))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sendMessageWithoutApiKeyEmitsErrorWithoutNetworkCall() = runTest {
        repository.sendMessage("missing-config", "gpt-4o", emptyList(), stream = true).test {
            val event = awaitItem() as ChatEvent.Error
            assertEquals(ErrorKind.KeyRemoved, event.error.kind)
            awaitComplete()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aSavedConfigWithNoKeyIsReportedAsNeedingOne() = runTest {
        val id = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()

        repository.sendMessage(id, "gpt-4o", emptyList(), stream = true).test {
            val event = awaitItem() as ChatEvent.Error
            assertEquals(ErrorKind.NoApiKey, event.error.kind)
            awaitComplete()
        }
    }

    @Test
    fun aKeyThisDeviceCannotDecryptIsNotReportedAsAMissingKey() = runTest {
        // Both look like "getApiKey returned null" from here, but they need opposite advice: one
        // is "add a key", the other is "the key you already added can't be read any more".
        val id = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        secureKeyRepository.saveApiKey(id, "sk-real-key")
        secureKeyRepository.simulateUndecryptable = true

        repository.sendMessage(id, "gpt-4o", emptyList(), stream = true).test {
            val event = awaitItem() as ChatEvent.Error
            assertEquals(ErrorKind.UndecryptableKey, event.error.kind)
            awaitComplete()
        }
    }

    @Test
    fun sendMessageWithApiKeyUsesConfiguredBaseUrlOverride() = runTest {
        val configId = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        secureKeyRepository.saveApiKey(configId, "sk-test")
        providerConfigRepository.setBaseUrlOverride(configId, server.url("/v1").toString().trimEnd('/'))
        server.enqueue(
            MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                .setHeader("Content-Type", "text/event-stream"),
        )

        repository.sendMessage(configId, "gpt-4o", emptyList(), stream = true).test {
            assertTrue(awaitItem() is ChatEvent.Started)
            assertEquals(ChatEvent.ContentDelta("Hi"), awaitItem())
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
    }
}
