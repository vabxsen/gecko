package com.gecko.core.provider.internal

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine bridge for a single OkHttp [Call]: unlike [Call.execute], cancelling the calling
 * coroutine actually cancels the underlying network call instead of leaving it running to
 * completion in the background.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) = continuation.resume(response)
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        },
    )
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}

/**
 * Executes [request] with up to [RetryPolicy.MAX_ATTEMPTS] tries, retrying only transient
 * failures ([RetryPolicy.isRetryableThrowable]/[RetryPolicy.isRetryableStatus]) with a backoff
 * delay between attempts. [request]'s body is a fixed byte source (never a one-shot stream) so
 * the same instance is safe to hand to a fresh [Call] on every attempt.
 */
internal suspend fun OkHttpClient.executeWithRetry(request: Request): Response {
    var attempt = 0
    while (true) {
        attempt++
        try {
            val response = newCall(request).await()
            val lastAttempt = attempt >= RetryPolicy.MAX_ATTEMPTS
            if (response.isSuccessful || lastAttempt || !RetryPolicy.isRetryableStatus(response.code)) {
                return response
            }
            response.close()
        } catch (e: IOException) {
            if (attempt >= RetryPolicy.MAX_ATTEMPTS || !RetryPolicy.isRetryableThrowable(e)) throw e
        }
        delay(RetryPolicy.backoffDelayMillis(attempt))
    }
}
