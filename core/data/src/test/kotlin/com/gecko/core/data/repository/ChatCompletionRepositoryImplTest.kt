package com.gecko.core.data.repository

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoException
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

    @Test fun documentSourceReachesTheProviderWithPageLabels() = runTest {
        val id = configuredKey()
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Friday [Page 2]"},"finish_reason":"stop"}]}"""))
        val message = com.gecko.core.model.chat.ChatMessage("m", "c", com.gecko.core.model.chat.MessageRole.USER,
            "What is the deadline?", java.time.Instant.EPOCH, com.gecko.core.model.chat.MessageStatus.COMPLETE,
            document = com.gecko.core.model.chat.DocumentAttachment("report.pdf", "[Page 2]\nThe deadline is Friday.", 2))
        repository.sendMessage(id, "gpt-4o-mini", listOf(message), false).collect {}
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("[Page 2]"))
        assertTrue(body.contains("The deadline is Friday."))
        assertTrue(body.contains("not instructions"))
        assertEquals("What is the deadline?", message.content)
    }

    private suspend fun configuredKey(): String {
        val id = providerConfigRepository.addProvider(ProviderId.OPENAI, "Test provider").getOrThrow()
        secureKeyRepository.saveApiKey(id, "test-key")
        providerConfigRepository.setBaseUrlOverride(id, server.url("/v1/").toString())
        return id
    }

    @Test
    fun connectionProbeReportsQuotaFailureInsteadOfConnected() = runTest {
        val id = configuredKey()
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o"}]}"""))
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"message":"Insufficient credits"}}"""))

        val failure = repository.testConnection(id).exceptionOrNull() as GeckoException

        assertEquals(ErrorKind.QuotaExhausted, failure.error.kind)
        assertEquals(id, failure.error.configId)
        assertEquals("/v1/models", server.takeRequest().path)
        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun publicCatalogCannotMakeAnInvalidKeyPassValidation() = runTest {
        val id = configuredKey()
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o"}]}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid key"}}"""))

        val failure = repository.testConnection(id).exceptionOrNull() as GeckoException

        assertEquals(ErrorKind.InvalidApiKey, failure.error.kind)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingChatEndpointDoesNotPassValidation() = runTest {
        val id = configuredKey()
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))

        val failure = repository.testConnection(id).exceptionOrNull() as GeckoException

        assertEquals(ErrorKind.ModelUnavailable, failure.error.kind)
    }

    @Test
    fun emptyCatalogDoesNotPassValidation() = runTest {
        val id = configuredKey()
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        val failure = repository.testConnection(id).exceptionOrNull() as GeckoException

        assertEquals(ErrorKind.ModelUnavailable, failure.error.kind)
        assertEquals(1, server.requestCount)
    }

    private lateinit var server: MockWebServer

    @Test
    fun automaticSetupSkipsAnInaccessibleModelUsingRealHttpRequests() = kotlinx.coroutines.runBlocking {
        val id = configuredKey()
        val prefs = com.gecko.core.testing.fake.FakeUserPreferencesRepository()
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"}]}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"Model access denied"}}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"))

        val result = com.gecko.domain.usecase.ConnectProviderUseCase(repository, providerConfigRepository, prefs)(id).getOrThrow()
        assertEquals("gpt-4o", result.modelId)
        assertEquals("gpt-4o", providerConfigRepository.currentConfig(id)?.verifiedModelId)
        assertEquals("gpt-4o", prefs.userPreferences.value.defaultModelId)
        assertEquals("/v1/models", server.takeRequest().path)
        assertTrue(server.takeRequest().body.readUtf8().contains("gpt-4o-mini"))
        assertTrue(server.takeRequest().body.readUtf8().contains("gpt-4o"))
    }

    @Test
    fun modelsWithoutStreamingSupportUseARegularCompletion() = runTest {
        val id = configuredKey()
        providerConfigRepository.saveModels(id, listOf(com.gecko.core.model.provider.ModelInfo(
            ProviderId.OPENAI, "test-model", "Test", 8192, false, false)))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}]}"""))
        repository.sendMessage(id, "test-model", emptyList(), stream = true).test {
            assertTrue(awaitItem() is ChatEvent.Started)
            assertEquals(ChatEvent.ContentDelta("Hello"), awaitItem())
            assertTrue(awaitItem() is ChatEvent.Completed)
            awaitComplete()
        }
        assertTrue(server.takeRequest().body.readUtf8().contains("\"stream\":false"))
    }
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
