package com.orca.core.network.sse

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun emitsEachSseEvent() = runTest {
        val body = "data: hello\n\n" + "event: done\ndata: {\"ok\":true}\n\n"
        server.enqueue(
            MockResponse()
                .setBody(body)
                .setHeader("Content-Type", "text/event-stream"),
        )
        val request = Request.Builder().url(server.url("/stream")).build()

        val events = client.streamSse(request).toList()

        assertEquals(2, events.size)
        assertNull(events[0].event)
        assertEquals("hello", events[0].data)
        assertEquals("done", events[1].event)
        assertEquals("{\"ok\":true}", events[1].data)
    }

    @Test
    fun failsWithSseExceptionForNon2xxResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"))
        val request = Request.Builder().url(server.url("/stream")).build()

        val result = runCatching { client.streamSse(request).toList() }

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SseException)
        assertEquals(401, (exception as SseException).httpCode)
    }
}
