package com.gecko.core.provider.openai

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: OpenAiProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        provider = OpenAiProvider(apiKey = "test-key", baseUrl = server.url("/v1").toString().trimEnd('/'), httpClient = client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamingEmitsDeltasThenCompleted() = runTest {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" there\"},\"finish_reason\":null}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}\n\n" +
            "data: [DONE]\n\n"
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hello"), awaitItem())
            assertEquals(ChatEvent.ContentDelta(" there"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            assertEquals(TokenUsage(10, 2, 12), completed.usage)
            awaitComplete()
        }
    }

    @Test
    fun nonStreamingEmitsSingleDeltaThenCompleted() = runTest {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"Hi there"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":5,"completion_tokens":3,"total_tokens":8}}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hi there"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            assertEquals(TokenUsage(5, 3, 8), completed.usage)
            awaitComplete()
        }
    }

    @Test
    fun attachedImageIsSentAsOpenAiImageUrlPart() = runTest {
        server.enqueue(MockResponse().setBody("{\"choices\":[]}").setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Describe this", attachmentImageBase64 = "aW1hZ2U=")), model = "gpt-4o", stream = false).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"type\":\"image_url\""))
        assertTrue(requestBody.contains("data:image/jpeg;base64,aW1hZ2U="))
    }

    @Test
    fun httpErrorDuringStreamEmitsChatEventError() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Invalid API key\"}}")
                .setHeader("Content-Type", "application/json"),
        )

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            val error = awaitItem() as ChatEvent.Error
            assertFalse(error.error.isRetryable)
            assertEquals(401, error.error.httpStatusCode)
            assertEquals("Invalid API key", error.error.technicalDetail)
            awaitComplete()
        }
    }

    @Test
    fun nonStreamingRetriesOnServerErrorThenSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}]}""")
                .setHeader("Content-Type", "application/json"),
        )

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hi"), awaitItem())
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun nonStreamingDoesNotRetryNonRetryableStatus() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Invalid API key\"}}")
                .setHeader("Content-Type", "application/json"),
        )

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            val error = awaitItem() as ChatEvent.Error
            assertFalse(error.error.isRetryable)
            assertEquals(401, error.error.httpStatusCode)
            awaitComplete()
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun streamingRetriesConnectionFailureBeforeAnyContent() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: [DONE]\n\n"
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hi"), awaitItem())
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun listModelsParsesIds() = runTest {
        server.enqueue(
            MockResponse().setBody("{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-4o-mini\"}]}")
                .setHeader("Content-Type", "application/json"),
        )

        val result = provider.listModels()

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals(2, models.size)
        assertEquals("gpt-4o", models[0].modelId)
        assertTrue(models[0].supportsImages)
    }
}
