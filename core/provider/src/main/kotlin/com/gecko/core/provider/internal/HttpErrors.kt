package com.gecko.core.provider.internal

import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.network.sse.SseException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

/**
 * The message is sanitised at construction rather than at the point of display, because there is
 * no single point of display — it reaches a dialog, a list-row subtitle and a persisted message
 * record. An unbounded body (a proxy's HTML 502 page, say) leaking into any of those is the same
 * bug three times over.
 */
internal class ProviderHttpException(val code: Int, message: String) :
    IOException(sanitizeProviderText(code, message))

private const val MAX_DETAIL_CHARS = 400

internal fun sanitizeProviderText(code: Int, body: String): String {
    // Unwrap before truncating, not after. Truncating first turns a long error body into invalid
    // JSON that nothing downstream can parse, which is how a perfectly clear "API key not valid"
    // ended up shown to the user as four hundred characters of Google's error envelope.
    val trimmed = body.trim().unwrapErrorEnvelope()
    return when {
        trimmed.isBlank() -> "HTTP $code"
        // A gateway or proxy answering instead of the API. Nothing in the page is worth showing.
        trimmed.startsWith("<") -> "HTTP $code — the provider returned a web page instead of a reply"
        trimmed.length > MAX_DETAIL_CHARS -> trimmed.take(MAX_DETAIL_CHARS) + "…"
        else -> trimmed
    }
}

/**
 * A failure the provider reported *inside* an otherwise-healthy response — an error object in a
 * `data:` frame mid-stream, or an `error` field alongside an empty `choices` list. There is no
 * HTTP status to attach unless the provider volunteered one in the payload, hence the nullable
 * [code]; [UNKNOWN_CODE] keeps it out of the retryable set, since a provider that has told us why
 * it failed is not going to change its mind on a second identical request.
 */
internal class ProviderReportedException(code: Int?, message: String) :
    IOException(message) {
    val statusCode: Int = code ?: UNKNOWN_CODE

    companion object {
        const val UNKNOWN_CODE = 0
    }
}

/**
 * The stream closed cleanly having produced no text at all and no error to explain it. Treated as
 * a failure rather than an empty reply: a blank assistant bubble tells the user nothing, and in
 * practice this means the model rejected the request in a way its wire format didn't express.
 */
internal class EmptyProviderResponseException : IOException("The model returned an empty response.")

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
            is ProviderReportedException -> current.statusCode
                .takeIf { it != ProviderReportedException.UNKNOWN_CODE }
                ?.let { return it }
            is SseException -> current.httpCode?.let { return it }
        }
        current = current.cause
    }
    return null
}

/**
 * Turns whatever a provider did into a named [ErrorKind].
 *
 * This replaced a three-case string map that had no answer for a wrong base URL (404), a
 * conversation grown past the context window (400), or no network at all — those all fell through
 * to raw vendor text or an OkHttp exception message, which is how "Unable to resolve host" ended
 * up being shown to people as an explanation.
 *
 * A few kinds can't be told apart by status code alone: 429 covers both "slow down" and "you're
 * out of credit", and 400 covers both "too long" and everything else. Those read the provider's
 * own wording, which is the only signal that distinguishes them.
 */
internal fun classifyProviderError(statusCode: Int?, detail: String?, cause: Throwable?): GeckoError {
    val body = detail.orEmpty().unwrapErrorEnvelope()
    val kind = when {
        statusCode == 401 || statusCode == 403 -> ErrorKind.InvalidApiKey
        // Google answers a bad key with 400 INVALID_ARGUMENT rather than 401, so status code alone
        // would file it under "bad request" and tell the user to try another model. Verified live.
        statusCode == 400 && KEY_WORDING.containsMatchIn(body) -> ErrorKind.InvalidApiKey
        statusCode == 402 -> ErrorKind.QuotaExhausted
        // A provider that names a retry delay is throttling, not out of money — Google's free tier
        // says "quota exceeded" for a per-minute cap that clears in seconds, and telling someone to
        // go and add billing when they just need to wait is worse than saying nothing.
        statusCode == 429 && RETRY_HINT.containsMatchIn(body) -> ErrorKind.RateLimited
        statusCode == 429 && QUOTA_WORDING.containsMatchIn(body) -> ErrorKind.QuotaExhausted
        statusCode == 429 -> ErrorKind.RateLimited
        statusCode == 404 -> ErrorKind.ModelUnavailable
        statusCode == 400 && CONTEXT_WORDING.containsMatchIn(body) -> ErrorKind.ContextTooLong
        statusCode == 400 && MODEL_WORDING.containsMatchIn(body) -> ErrorKind.ModelUnavailable
        statusCode == 400 -> ErrorKind.BadRequest
        statusCode != null && statusCode in 500..599 -> ErrorKind.ProviderOutage
        cause is EmptyProviderResponseException -> ErrorKind.EmptyResponse
        cause.isConnectivityFailure() -> ErrorKind.Offline
        // A provider that explained itself in its payload but named no status.
        cause is ProviderReportedException -> ErrorKind.Unknown
        statusCode == null -> ErrorKind.Offline
        else -> ErrorKind.Unknown
    }
    return GeckoError(
        kind = kind,
        technicalDetail = body.takeIf { it.isNotBlank() },
        httpStatusCode = statusCode,
    )
}

/**
 * The single conversion from "something was thrown" to "here is what went wrong", used by every
 * provider's terminal `catch`. A [GeckoException] already knows its own answer — that's how a
 * classification made deep inside a stream (a mid-stream error frame, a blocked prompt) survives
 * the trip out.
 */
internal fun Throwable.toGeckoError(): GeckoError = when (this) {
    is GeckoException -> error
    else -> classifyProviderError(
        statusCode = httpStatusCodeOrNull(),
        detail = message,
        cause = this,
    )
}

/**
 * Pulls the human sentence out of `{"error":{"message":"…"}}`, the envelope OpenAI, Anthropic and
 * Google all happen to share.
 *
 * The non-streaming paths already decode this with their own typed serializers, but a failure that
 * arrives over SSE reaches us as an undecoded body string — which is how a perfectly good "Invalid
 * API key" was being shown to people still wrapped in its JSON.
 */
private fun String.unwrapErrorEnvelope(): String {
    if (!trimStart().startsWith("{")) return this
    val message = runCatching {
        ProviderJson.parseToJsonElement(this)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()
    return message?.takeIf { it.isNotBlank() } ?: this
}

/** "Please retry in 18.4s", "try again in 30 seconds" — a wait, not a bill. */
private val RETRY_HINT = Regex("""retry (in|after)|try again in""", RegexOption.IGNORE_CASE)
private val KEY_WORDING = Regex(
    """api.?key|api_key_invalid|invalid.{0,20}credential|unauthenticated""",
    RegexOption.IGNORE_CASE,
)
private val QUOTA_WORDING = Regex(
    """quota|billing|insufficient|credit|balance|payment|exceeded your current""",
    RegexOption.IGNORE_CASE,
)
private val CONTEXT_WORDING = Regex(
    """context.{0,20}(length|window|limit)|too many tokens|maximum.{0,20}tokens|too long""",
    RegexOption.IGNORE_CASE,
)
private val MODEL_WORDING = Regex(
    """model.{0,30}(not found|does not exist|unavailable|not supported|invalid)""",
    RegexOption.IGNORE_CASE,
)

/**
 * Whether the request never reached the provider at all. Deliberately excludes the provider's own
 * exception types — one of those means we got an answer we didn't like, which is a different
 * problem with a different fix.
 */
private fun Throwable?.isConnectivityFailure(): Boolean = generateSequence(this) { it.cause }.any {
    when (it) {
        is ProviderHttpException, is ProviderReportedException, is EmptyProviderResponseException -> false
        is UnknownHostException, is ConnectException, is SocketTimeoutException, is SSLException -> true
        is SseException -> it.httpCode == null
        is IOException -> true
        else -> false
    }
}
