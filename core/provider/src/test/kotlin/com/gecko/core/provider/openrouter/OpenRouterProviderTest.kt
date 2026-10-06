package com.gecko.core.provider.openrouter

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.provider.userMessage
import kotlinx.coroutines.flow.toList
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
        assertEquals("Gecko", recorded.getHeader("X-Title"))
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

    @Test fun choiceErrorAfterPartialTextDoesNotCompleteOrReplayTheReply() = runTest {
        enqueueStream("""
            data: {"choices":[{"delta":{"content":"Partial"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"error","error":{"code":503,"message":"Model unavailable"}}]}

            data: [DONE]

        """.trimIndent())
        val events = provider.sendMessage(listOf(userMessage("Hi")), "openrouter/free", true).toList()
        assertEquals(ChatEvent.ContentDelta("Partial"), events[1])
        assertEquals(ErrorKind.ProviderOutage, (events.last() as ChatEvent.Error).error.kind)
        assertTrue(events.none { it is ChatEvent.Completed })
        assertEquals(1, server.requestCount)
    }

    @Test fun errorFinishReasonWithoutDetailsIsStillAFailure() = runTest {
        enqueueStream("data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"},\"finish_reason\":\"error\"}]}\n\ndata: [DONE]\n\n")
        val events = provider.sendMessage(listOf(userMessage("Hi")), "openrouter/free", true).toList()
        assertTrue(events.last() is ChatEvent.Error)
        assertTrue(events.none { it is ChatEvent.Completed })
    }

    @Test fun nonStreamingChoiceErrorsAreNotSuccessfulAnswers() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Partial"},"finish_reason":"error","error":{"code":402,"message":"Insufficient credits"}}]}"""))
        val events = provider.sendMessage(listOf(userMessage("Hi")), "vendor/model", false).toList()
        assertEquals(ErrorKind.QuotaExhausted, (events.last() as ChatEvent.Error).error.kind)
        assertTrue(events.none { it is ChatEvent.Completed || it is ChatEvent.ContentDelta })
    }

    @Test fun commentsAndFinalUsageChunkPreserveTheAnswerAndTokenCount() = runTest {
        enqueueStream("""
            : OPENROUTER PROCESSING

            data: {"choices":[{"delta":{"reasoning":"Thinking"}}]}

            data: {"choices":[{"delta":{"content":"OK"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}

            data: [DONE]

        """.trimIndent())
        val events = provider.sendMessage(listOf(userMessage("Hi")), "openrouter/free", true).toList()
        assertEquals(listOf(ChatEvent.Started(), ChatEvent.ContentDelta("OK"), ChatEvent.Completed(FinishReason.STOP, TokenUsage(5, 2, 7))), events)
    }

    @Test fun invalidKeyIsReportedWithoutRetrying() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":401,"message":"Invalid API key"}}"""))
        val events = provider.sendMessage(listOf(userMessage("Hi")), "openrouter/free", true).toList()
        assertEquals(ErrorKind.InvalidApiKey, (events.last() as ChatEvent.Error).error.kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun catalogUsesInputCapabilitiesAndExcludesModelsWithoutTextOutput() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[
            {"id":"vendor/vision","architecture":{"input_modalities":["text","image"],"output_modalities":["text"]}},
            {"id":"vendor/draw","architecture":{"modality":"text->image","input_modalities":["text"],"output_modalities":["image"]}},
            {"id":"vendor/mixed","architecture":{"modality":"text->text+image"}}
        ]}"""))
        val models = provider.listModels().getOrThrow()
        assertEquals(listOf("vendor/vision", "vendor/mixed"), models.map { it.modelId })
        assertTrue(models[0].supportsImages)
        assertEquals(false, models[1].supportsImages)
    }

    private fun enqueueStream(body: String) {
        server.enqueue(MockResponse().setBody(body + "\n\n").setHeader("Content-Type", "text/event-stream"))
    }
}
