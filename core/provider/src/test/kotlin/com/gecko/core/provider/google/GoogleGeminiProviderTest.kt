package com.gecko.core.provider.google

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.provider.userMessage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GoogleGeminiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GoogleGeminiProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        provider = GoogleGeminiProvider(apiKey = "test-key", baseUrl = server.url("/v1beta").toString().trimEnd('/'), httpClient = client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamingEmitsDeltasThenCompleted() = runTest {
        val body = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}],\"role\":\"model\"}}]}\n\n" +
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" there\"}],\"role\":\"model\"}," +
            "\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":7,\"candidatesTokenCount\":2,\"totalTokenCount\":9}}\n\n"
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "gemini-1.5-pro", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hello"), awaitItem())
            assertEquals(ChatEvent.ContentDelta(" there"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            assertEquals(TokenUsage(7, 2, 9), completed.usage)
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertTrue(recorded.path.orEmpty().contains("streamGenerateContent"))
        assertTrue(recorded.path.orEmpty().contains("alt=sse"))
    }

    @Test
    fun nonStreamingEmitsSingleDeltaThenCompleted() = runTest {
        val body = """
            {"candidates":[{"content":{"parts":[{"text":"Hi there"}],"role":"model"},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":3,"totalTokenCount":8}}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "gemini-1.5-pro", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hi there"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            assertEquals(TokenUsage(5, 3, 8), completed.usage)
            awaitComplete()
        }
    }

    @Test
    fun listModelsStripsNamePrefixAndUsesTokenLimit() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "{\"models\":[{\"name\":\"models/gemini-1.5-pro\",\"displayName\":\"Gemini 1.5 Pro\"," +
                    "\"inputTokenLimit\":2000000,\"supportedGenerationMethods\":[\"generateContent\",\"streamGenerateContent\"]}]}",
            ).setHeader("Content-Type", "application/json"),
        )

        val result = provider.listModels()

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals(1, models.size)
        assertEquals("gemini-1.5-pro", models[0].modelId)
        assertEquals(2_000_000, models[0].contextWindowTokens)
    }
}
