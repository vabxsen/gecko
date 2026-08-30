package com.orca.core.provider.openrouter

import app.cash.turbine.test
import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.FinishReason
import com.orca.core.provider.userMessage
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

class OpenRouterProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: OpenRouterProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        provider = OpenRouterProvider(apiKey = "test-key", baseUrl = server.url("/api/v1").toString().trimEnd('/'), httpClient = client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamingReusesOpenAiWireFormat() = runTest {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: [DONE]\n\n"
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "openai/gpt-4o", stream = true).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hello"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertEquals("Orca", recorded.getHeader("X-Title"))
    }

    @Test
    fun listModelsUsesContextLengthAndModality() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "{\"data\":[{\"id\":\"openai/gpt-4o\",\"name\":\"GPT-4o\",\"context_length\":128000," +
                    "\"architecture\":{\"modality\":\"text+image->text\"}}]}",
            ).setHeader("Content-Type", "application/json"),
        )

        val result = provider.listModels()

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals(1, models.size)
        assertEquals(128_000, models[0].contextWindowTokens)
        assertTrue(models[0].supportsImages)
    }
}
