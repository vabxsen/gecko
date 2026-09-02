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
    fun attachedImageIsSentAsGeminiInlineData() = runTest {
        server.enqueue(MockResponse().setBody("{\"candidates\":[]}").setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Describe this", attachmentImageBase64 = "aW1hZ2U=")), model = "gemini-2.5-flash", stream = false).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"inlineData\":{\"mimeType\":\"image/jpeg\",\"data\":\"aW1hZ2U=\"}"))
    }

    @Test
    fun streamingEmitsImageDeltaForInlineDataParts() = runTest {
        val body = "data: {\"candidates\":[{\"content\":{\"parts\":[" +
            "{\"text\":\"Here you go:\"}," +
            "{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"iVBORw0KGgo=\"}}" +
            "],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}\n\n"
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream"))

        provider.sendMessage(listOf(userMessage("Draw something")), model = "gemini-2.5-flash-image", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Here you go:"), awaitItem())
            assertEquals(ChatEvent.ImageDelta(base64 = "iVBORw0KGgo=", mimeType = "image/png"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            awaitComplete()
        }
    }

    @Test
    fun nonStreamingEmitsImageDeltaForInlineDataParts() = runTest {
        val body = """
            {"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"AAAA"}}],"role":"model"},"finishReason":"STOP"}]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Draw a circle")), model = "gemini-2.5-flash-image", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ImageDelta(base64 = "AAAA", mimeType = "image/png"), awaitItem())
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
    }

    @Test
    fun imageOutputModelRequestsBothResponseModalities() = runTest {
        server.enqueue(MockResponse().setBody("{\"candidates\":[]}").setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Draw a cat")), model = "gemini-2.5-flash-image", stream = false).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"responseModalities\":[\"TEXT\",\"IMAGE\"]"))
    }

    @Test
    fun regularChatModelDoesNotRequestResponseModalities() = runTest {
        server.enqueue(MockResponse().setBody("{\"candidates\":[]}").setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "gemini-2.5-flash", stream = false).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(!requestBody.contains("responseModalities"))
        assertTrue(!requestBody.contains("generationConfig"))
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

    @Test
    fun nonStreamingRetriesOnServerErrorThenSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        server.enqueue(
            MockResponse()
                .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Recovered\"}]}}]}")
                .setHeader("Content-Type", "application/json"),
        )

        provider.sendMessage(listOf(userMessage("Hi")), model = "gemini-2.5-flash", stream = false).test {
            awaitItem()
            assertEquals("Recovered", (awaitItem() as ChatEvent.ContentDelta).text)
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun nonStreamingDoesNotRetryNonRetryableStatus() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"bad request\"}}")
                .setHeader("Content-Type", "application/json"),
        )

        provider.sendMessage(listOf(userMessage("Hi")), model = "gemini-2.5-flash", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            val error = awaitItem() as ChatEvent.Error
            assertEquals(false, error.error.isRetryable)
            assertEquals(400, error.error.httpStatusCode)
            awaitComplete()
        }
        assertEquals(1, server.requestCount)
    }
}
