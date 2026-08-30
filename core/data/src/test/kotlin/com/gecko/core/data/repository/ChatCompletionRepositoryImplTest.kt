package com.gecko.core.data.repository

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
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
        repository.sendMessage(ProviderId.OPENAI, "gpt-4o", emptyList(), stream = true).test {
            val event = awaitItem() as ChatEvent.Error
            assertFalse(event.isRetryable)
            awaitComplete()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sendMessageWithApiKeyUsesConfiguredBaseUrlOverride() = runTest {
        secureKeyRepository.saveApiKey(ProviderId.OPENAI, "sk-test")
        providerConfigRepository.setBaseUrlOverride(ProviderId.OPENAI, server.url("/v1").toString().trimEnd('/'))
        server.enqueue(
            MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                .setHeader("Content-Type", "text/event-stream"),
        )

        repository.sendMessage(ProviderId.OPENAI, "gpt-4o", emptyList(), stream = true).test {
            assertTrue(awaitItem() is ChatEvent.Started)
            assertEquals(ChatEvent.ContentDelta("Hi"), awaitItem())
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
    }
}
