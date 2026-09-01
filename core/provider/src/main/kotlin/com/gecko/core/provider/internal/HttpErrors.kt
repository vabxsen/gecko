package com.gecko.core.provider.internal

import com.gecko.core.network.sse.SseException
import java.io.IOException
import okhttp3.Response

internal class ProviderHttpException(val code: Int, message: String) : IOException(message)

/** Reads the response body, throwing [ProviderHttpException] if the call was not successful. */
internal fun Response.bodyOrThrow(): String {
    val successful = isSuccessful
    val code = code
    val fallbackMessage = message
    val bodyText = use { response -> response.body?.string().orEmpty() }
    if (!successful) {
        throw ProviderHttpException(code = code, message = bodyText.ifBlank { fallbackMessage })
    }
    return bodyText
}

/**
 * Walks the [Throwable.cause] chain looking for the HTTP status code of a provider failure.
 * Each provider's `toReadableException()` wraps the original [ProviderHttpException] as its
 * `cause`, so this works without any extra exception-wrapping at the call sites.
 */
internal fun Throwable.httpStatusCodeOrNull(): Int? {
    var current: Throwable? = this
    while (current != null) {
        when (current) {
            is ProviderHttpException -> return current.code
            is SseException -> current.httpCode?.let { return it }
        }
        current = current.cause
    }
    return null
}

/** A short, user-actionable message for a known status code; falls back to the raw provider text. */
internal fun friendlyMessageFor(statusCode: Int?, fallback: String): String = when (statusCode) {
    401, 403 -> "Invalid API key — check it in Settings."
    429 -> "Rate limited by the provider — try again in a moment."
    in 500..599 -> "The provider is having issues right now — try again shortly."
    else -> fallback
}
