package com.gecko.core.model.error

/**
 * What went wrong, in terms the app can act on.
 *
 * Errors used to travel as free-form strings, which meant every failure looked identical to the
 * code that had to respond to one: a bad key, a dead network and a retired model all arrived as a
 * sentence, so the only thing the app could do with any of them was show it and forget it. A named
 * kind is what lets the same failure drive a title, an explanation, and a button that fixes it.
 *
 * [wireName] is persisted in Room and must never change for an existing entry.
 */
enum class ErrorKind(val wireName: String) {
    InvalidApiKey("INVALID_API_KEY"),
    NoApiKey("NO_API_KEY"),

    /** The key is stored but this device can no longer decrypt it — not the same as having none. */
    UndecryptableKey("UNDECRYPTABLE_KEY"),
    RateLimited("RATE_LIMITED"),
    QuotaExhausted("QUOTA_EXHAUSTED"),
    ModelUnavailable("MODEL_UNAVAILABLE"),
    ContextTooLong("CONTEXT_TOO_LONG"),
    BadRequest("BAD_REQUEST"),
    ProviderOutage("PROVIDER_OUTAGE"),
    Offline("OFFLINE"),
    SafetyBlocked("SAFETY_BLOCKED"),

    /** The answer hit the model's length limit. A note on a real reply, not a failed one. */
    Truncated("TRUNCATED"),
    EmptyResponse("EMPTY_RESPONSE"),
    KeyRemoved("KEY_REMOVED"),
    Unknown("UNKNOWN"),
    ;

    companion object {
        fun fromWireName(name: String?): ErrorKind? = entries.find { it.wireName == name }
    }
}

/** The single action a failure's fix button performs. */
enum class ErrorFix {
    Retry,
    OpenProviderKey,
    PickAnotherModel,
    StartNewChat,
    None,
}

/**
 * A failure, ready to be explained to someone.
 *
 * [technicalDetail] is the provider's own wording, already truncated and stripped of markup. It
 * belongs behind a "Details" affordance, never as the headline — an HTML error page or a stack of
 * vendor JSON is not an explanation.
 */
data class GeckoError(
    val kind: ErrorKind,
    val technicalDetail: String? = null,
    val httpStatusCode: Int? = null,
    /** Which saved key this concerns, so a fix button can open the right screen. */
    val configId: String? = null,
    val providerLabel: String? = null,
) {
    /** Whether sending the same request again could plausibly succeed. */
    val isRetryable: Boolean
        get() = when (kind) {
            ErrorKind.RateLimited,
            ErrorKind.ProviderOutage,
            ErrorKind.Offline,
            ErrorKind.EmptyResponse,
            -> true
            else -> false
        }

    /**
     * A truncated answer is still an answer — worth a footnote in the transcript, never worth
     * interrupting someone with a dialog.
     */
    val deservesInterrupting: Boolean
        get() = kind != ErrorKind.Truncated
}

/** Carries a [GeckoError] across a `throw` or `Result.failure` boundary. */
class GeckoException(val error: GeckoError) :
    Exception(error.technicalDetail ?: error.kind.wireName)
