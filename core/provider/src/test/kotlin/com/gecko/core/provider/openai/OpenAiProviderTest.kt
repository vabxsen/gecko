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
    fun httpErrorDuringStreamEmitsChatEventError() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Invalid API key\"}}")
                .setHeader("Content-Type", "application/json"),
        )

        provider.sendMessage(listOf(userMessage("Hi")), model = "gpt-4o", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            val error = awaitItem() as ChatEvent.Error
            assertTrue(error.isRetryable)
            awaitComplete()
        }
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
