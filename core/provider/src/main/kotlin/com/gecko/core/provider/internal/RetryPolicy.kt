package com.gecko.core.provider.internal

import com.gecko.core.network.sse.SseException
import java.io.IOException

/**
 * Shared retry policy for transient provider failures. Deliberately conservative: only HTTP
 * codes that mean "try again later" (rate limit, server-side trouble) and connection-level
 * network blips are retryable — anything else (401/403/400/404/etc.) fails immediately, since
 * retrying a permanent failure just wastes time and delays the real error reaching the user.
 */
internal object RetryPolicy {
    const val MAX_ATTEMPTS = 3
    private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)

    fun isRetryableStatus(code: Int): Boolean = code in RETRYABLE_HTTP_CODES

    fun isRetryableThrowable(e: Throwable): Boolean = when (e) {
        is ProviderHttpException -> isRetryableStatus(e.code)
        // No HTTP code at all means the connection itself failed (never got a response), which
        // is the same class of transient failure as a 5xx.
        is SseException -> e.httpCode?.let(::isRetryableStatus) ?: true
        is IOException -> true
        else -> false
    }

    fun backoffDelayMillis(attempt: Int): Long = (500L shl (attempt - 1)).coerceAtMost(4000L)
}
