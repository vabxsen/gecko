package com.orca.core.network.sse

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

class SseException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Opens a server-sent-events stream for [request] and emits each event as it arrives.
 * The connection is cancelled when the returned flow's collector is cancelled.
 */
fun OkHttpClient.streamSse(request: Request): Flow<SseEvent> = callbackFlow {
    val listener = object : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            trySend(SseEvent(event = type, data = data, id = id))
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            val bodyText = try {
                response?.body?.string()
            } catch (_: Exception) {
                null
            }
            val message = bodyText?.takeIf { it.isNotBlank() }
                ?: t?.message
                ?: "SSE connection failed"
            close(SseException(message = message, httpCode = response?.code, cause = t))
        }

        override fun onClosed(eventSource: EventSource) {
            close()
        }
    }

    val eventSource = EventSources.createFactory(this@streamSse).newEventSource(request, listener)

    awaitClose { eventSource.cancel() }
}
