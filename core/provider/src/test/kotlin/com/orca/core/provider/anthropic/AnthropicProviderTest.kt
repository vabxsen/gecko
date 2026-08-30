package com.orca.core.provider.anthropic

import app.cash.turbine.test
import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.FinishReason
import com.orca.core.model.chat.TokenUsage
import com.orca.core.provider.systemMessage
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

class AnthropicProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: AnthropicProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        provider = AnthropicProvider(apiKey = "test-key", baseUrl = server.url("/v1").toString().trimEnd('/'), httpClient = client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamingEmitsDeltasThenCompleted() = runTest {
        val body = "event: message_start\n" +
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10}}}\n\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" there\"}}\n\n" +
            "event: message_delta\n" +
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}\n\n" +
            "event: message_stop\n" +
            "data: {\"type\":\"message_stop\"}\n\n"
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream"))

        provider.sendMessage(
            listOf(systemMessage("Be terse"), userMessage("Hi")),
            model = "claude-3-5-sonnet-20241022",
            stream = true,
        ).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hello"), awaitItem())
            assertEquals(ChatEvent.ContentDelta(" there"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            assertEquals(TokenUsage(10, 5, 15), completed.usage)
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertTrue(recorded.body.readUtf8().contains("\"system\":\"Be terse\""))
    }

    @Test
    fun nonStreamingEmitsSingleDeltaThenCompleted() = runTest {
        val body = """{"content":[{"type":"text","text":"Hi there"}],"stop_reason":"end_turn","usage":{"input_tokens":5,"output_tokens":3}}"""
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        provider.sendMessage(listOf(userMessage("Hi")), model = "claude-3-5-sonnet-20241022", stream = false).test {
            assertEquals(ChatEvent.Started(), awaitItem())
            assertEquals(ChatEvent.ContentDelta("Hi there"), awaitItem())
            val completed = awaitItem() as ChatEvent.Completed
            assertEquals(FinishReason.STOP, completed.finishReason)
            assertEquals(TokenUsage(5, 3, 8), completed.usage)
            awaitComplete()
        }
    }

    @Test
    fun listModelsParsesDisplayNames() = runTest {
        server.enqueue(
            MockResponse().setBody("{\"data\":[{\"id\":\"claude-3-5-sonnet-20241022\",\"display_name\":\"Claude 3.5 Sonnet\"}]}")
                .setHeader("Content-Type", "application/json"),
        )

        val result = provider.listModels()

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals(1, models.size)
        assertEquals("Claude 3.5 Sonnet", models[0].displayName)
        assertEquals(200_000, models[0].contextWindowTokens)
    }
}
