package com.orca.core.provider.internal

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
