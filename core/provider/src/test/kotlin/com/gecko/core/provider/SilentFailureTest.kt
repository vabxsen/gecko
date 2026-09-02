package com.gecko.core.provider

import app.cash.turbine.test
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoException
import com.gecko.core.provider.anthropic.AnthropicProvider
import com.gecko.core.provider.google.GoogleGeminiProvider
import com.gecko.core.provider.openai.OpenAiProvider
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

/**
 * Every case here used to reach the user as an empty assistant bubble with no explanation at all —
 * the single worst failure mode the app had, because it looks identical to the app being broken.
 * A provider that fails must say so; these pin that promise for each wire format.
 */
class SilentFailureTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private fun openAi() = OpenAiProvider(
        apiKey = "test-key",
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        httpClient = client,
    )

    private fun gemini() = GoogleGeminiProvider(
        apiKey = "test-key",
        baseUrl = server.url("/v1beta").toString().trimEnd('/'),
        httpClient = client,
    )

    private fun anthropic() = AnthropicProvider(
        apiKey = "test-key",
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        httpClient = client,
    )

    private fun sse(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "text/event-stream")
    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    // --- Errors delivered inside a 200 stream --------------------------------------------------

    @Test
    fun openAiProtocolReportsAnErrorSentAsAStreamFrame() = runTest {
        // How OpenRouter and NVIDIA NIM report a mid-stream failure: a normal 200 SSE response
        // whose only payload is an error object. Discarding unrecognised frames swallowed it.
        server.enqueue(sse("data: {\"error\":{\"message\":\"Insufficient credits\",\"code\":402}}\n\ndata: [DONE]\n\n"))

        openAi().sendMessage(listOf(userMessage("Hi")), model = "some-model", stream = true).test {
            awaitItem()
            val error = awaitItem() as ChatEvent.Error
            assertEquals(ErrorKind.QuotaExhausted, error.error.kind)
            assertTrue(error.error.technicalDetail.orEmpty().contains("credits", ignoreCase = true))
            awaitComplete()
        }
    }

    @Test
    fun geminiReportsAnErrorSentAsAStreamFrame() = runTest {
        server.enqueue(sse("data: {\"error\":{\"message\":\"Model overloaded\"}}\n\n"))

        gemini().sendMessage(listOf(userMessage("Hi")), model = "gemini-2.5-flash", stream = true).test {
            awaitItem()
            val error = awaitItem() as ChatEvent.Error
            assertTrue(error.error.technicalDetail.orEmpty().contains("overloaded", ignoreCase = true))
            awaitComplete()
        }
    }

    @Test
    fun geminiReportsABlockedPrompt() = runTest {
        // A safety refusal returns 200 with no candidates at all.
        server.enqueue(sse("data: {\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}\n\n"))

        gemini().sendMessage(listOf(userMessage("Hi")), model = "gemini-2.5-flash", stream = true).test {
            awaitItem()
            val error = awaitItem() as ChatEvent.Error
            assertTrue(error.error.technicalDetail.orEmpty().contains("safety", ignoreCase = true))
            awaitComplete()
        }
    }

    @Test
    fun anthropicStreamErrorIsClassifiedLikeEveryOtherFailure() = runTest {
        server.enqueue(sse("data: {\"type\":\"error\",\"error\":{\"message\":\"Overloaded\"}}\n\n"))

        anthropic().sendMessage(listOf(userMessage("Hi")), model = "claude-sonnet-4-5", stream = true).test {
            awaitItem()
            val error = awaitItem() as ChatEvent.Error
            assertTrue(error.error.technicalDetail.orEmpty().contains("Overloaded", ignoreCase = true))
            awaitComplete()
        }
    }

    // --- Replies that arrive carrying nothing --------------------------------------------------

    @Test
    fun aStreamThatEndsWithNoContentIsAnErrorNotAnEmptyReply() = runTest {
        server.enqueue(sse("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"))

        openAi().sendMessage(listOf(userMessage("Hi")), model = "some-model", stream = true).test {
            awaitItem()
            assertTrue(awaitItem() is ChatEvent.Error)
            awaitComplete()
        }
    }

    @Test
    fun aNullContentReplyIsAnErrorNotASerializerCrash() = runTest {
        // Reasoning models and refusals both send an explicit null here. Decoding it into a
        // non-null String threw kotlinx's own exception text at the user.
        server.enqueue(json("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null},\"finish_reason\":\"stop\"}]}"))

        openAi().sendMessage(listOf(userMessage("Hi")), model = "some-model", stream = false).test {
            awaitItem()
            val error = awaitItem() as ChatEvent.Error
            assertEquals(ErrorKind.EmptyResponse, error.error.kind)
            assertTrue(
                "raw serializer jargon leaked",
                !error.error.technicalDetail.orEmpty().contains("serial", ignoreCase = true),
            )
            awaitComplete()
        }
    }

    // --- Reasoning models ----------------------------------------------------------------------

    @Test
    fun aModelThatAnswersOnlyInReasoningContentStillShowsItsAnswer() = runTest {
        // NVIDIA's Nemotron thinking tiers and DeepSeek's reasoner stream here instead of into
        // `content`; parsing only `content` rendered the whole reply as a blank bubble.
        server.enqueue(
            sse(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"Working\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\" it out.\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                    "data: [DONE]\n\n",
            ),
        )

        openAi().sendMessage(listOf(userMessage("Hi")), model = "nemotron", stream = true).test {
            awaitItem()
            assertEquals("Working it out.", (awaitItem() as ChatEvent.ContentDelta).text)
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
    }

    @Test
    fun realContentWinsOverReasoningContentWhenBothArrive() = runTest {
        // A model that thinks *and* answers should show only the answer — the working-out is not
        // what the user asked for, and concatenating both would read as gibberish.
        server.enqueue(
            sse(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hmm\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"42\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                    "data: [DONE]\n\n",
            ),
        )

        openAi().sendMessage(listOf(userMessage("Hi")), model = "nemotron", stream = true).test {
            awaitItem()
            assertEquals("42", (awaitItem() as ChatEvent.ContentDelta).text)
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
    }

    @Test
    fun aNonStreamingReasoningOnlyReplyStillShowsItsAnswer() = runTest {
        server.enqueue(
            json(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null," +
                    "\"reasoning_content\":\"The answer is 42.\"},\"finish_reason\":\"stop\"}]}",
            ),
        )

        openAi().sendMessage(listOf(userMessage("Hi")), model = "nemotron", stream = false).test {
            awaitItem()
            assertEquals("The answer is 42.", (awaitItem() as ChatEvent.ContentDelta).text)
            awaitItem() as ChatEvent.Completed
            awaitComplete()
        }
    }

    @Test
    fun streamOptionsCarriesIncludeUsageOnTheWire() = runTest {
        // A default value made kotlinx omit this field, so `stream_options` went out as `{}`.
        // NVIDIA NIM answers that with `400 missing field 'include_usage'` — every streamed
        // message to NVIDIA failed, which is the whole "Nemotron doesn't work" report.
        server.enqueue(sse("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n"))

        openAi().sendMessage(listOf(userMessage("Hi")), model = "nemotron", stream = true).test {
            awaitItem()
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val body = server.takeRequest().body.readUtf8()
        assertTrue("stream_options went out empty: $body", body.contains("\"include_usage\":true"))
    }

    // --- Classification quirks found by running against the real APIs ---------------------------

    @Test
    fun googlesBadKeyResponseIsReadAsARejectedKeyNotABadRequest() = runTest {
        // Google answers an invalid key with 400 INVALID_ARGUMENT, not 401. Going by status code
        // alone filed it as "bad request" and told the user to try a different model. This body is
        // the real one, captured from the live API.
        server.enqueue(
            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json").setBody(
                """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.",""" +
                    """"status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",""" +
                    """"reason":"API_KEY_INVALID","domain":"googleapis.com","metadata":{"service":"x"}}]}}""",
            ),
        )

        val failure = gemini().listModels().exceptionOrNull() as GeckoException

        assertEquals(ErrorKind.InvalidApiKey, failure.error.kind)
    }

    @Test
    fun aLongErrorBodyIsReducedToItsSentenceNotItsFirstFewHundredCharacters() = runTest {
        // Truncating the body before parsing left invalid JSON that nothing downstream could
        // unwrap, so the raw envelope reached the user. The sentence has to survive the trip.
        val padding = "x".repeat(2000)
        server.enqueue(
            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":400,"message":"API key not valid.","details":"$padding"}}"""),
        )

        val failure = gemini().listModels().exceptionOrNull() as GeckoException

        assertEquals("API key not valid.", failure.error.technicalDetail)
    }

    // --- Catalog paging ------------------------------------------------------------------------

    @Test
    fun geminiCollectsEveryPageOfItsModelCatalog() = runTest {
        // Google returns 50 per page by default. Stopping at page one hid models the curated
        // shortlist depends on, which quietly degraded the picker back to the raw catalog.
        server.enqueue(
            json(
                "{\"models\":[{\"name\":\"models/gemini-a\",\"displayName\":\"A\"," +
                    "\"supportedGenerationMethods\":[\"generateContent\"]}],\"nextPageToken\":\"page2\"}",
            ),
        )
        server.enqueue(
            json(
                "{\"models\":[{\"name\":\"models/gemini-b\",\"displayName\":\"B\"," +
                    "\"supportedGenerationMethods\":[\"generateContent\"]}]}",
            ),
        )

        val models = gemini().listModels().getOrThrow()

        assertEquals(listOf("gemini-a", "gemini-b"), models.map { it.modelId })
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().path!!.contains("pageSize="))
        assertTrue(server.takeRequest().path!!.contains("pageToken=page2"))
    }
}
