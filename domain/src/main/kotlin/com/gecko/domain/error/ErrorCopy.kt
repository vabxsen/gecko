package com.gecko.domain.error

import com.gecko.core.model.error.ErrorFix
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError

/**
 * Everything the UI needs to explain one failure. Copy is app policy rather than a model concern,
 * so it lives here where both features can reach it and `core:provider` never has to.
 */
data class ErrorCopy(
    /** Dialog headline. A plain statement of what happened, not a category name. */
    val title: String,
    /** What happened and what to do about it, in the second person. */
    val explanation: String,
    val fix: ErrorFix,
    /** Label for the fix button; null when [fix] is [ErrorFix.None] and there's only a dismiss. */
    val fixLabel: String?,
    /** Two or three words, for a list row subtitle where a sentence won't fit. */
    val shortLabel: String,
)

/**
 * Turns a failure into something worth reading.
 *
 * The rule behind every line below: name the thing that broke, say whose side it's on, and give
 * one concrete next step. "Invalid API key — check it in Settings" (the old copy) fails the last
 * two — it doesn't say who rejected it, and "check it" isn't an action.
 */
fun GeckoError.copyForUser(): ErrorCopy {
    val provider = providerLabel ?: "this provider"
    return when (kind) {
        ErrorKind.InvalidApiKey -> ErrorCopy(
            title = "That API key was rejected",
            explanation = "$provider didn't accept this key. It may have been revoked, or copied " +
                "with a character missing. Open the key and paste it again.",
            fix = ErrorFix.OpenProviderKey,
            fixLabel = "Open key",
            shortLabel = "Key rejected",
        )

        ErrorKind.NoApiKey -> ErrorCopy(
            title = "No API key for $provider",
            explanation = "Add a key for $provider before chatting with it.",
            fix = ErrorFix.OpenProviderKey,
            fixLabel = "Add key",
            shortLabel = "No API key",
        )

        ErrorKind.UndecryptableKey -> ErrorCopy(
            title = "Your saved key can't be read",
            explanation = "This device can no longer decrypt the key you saved for $provider. " +
                "That can happen after a system update or a screen-lock change. Paste the key " +
                "again to fix it.",
            fix = ErrorFix.OpenProviderKey,
            fixLabel = "Re-enter key",
            shortLabel = "Key unreadable",
        )

        ErrorKind.RateLimited -> ErrorCopy(
            title = "Too many requests",
            explanation = "$provider is asking you to slow down. Wait a few seconds and try again.",
            fix = ErrorFix.Retry,
            fixLabel = "Try again",
            shortLabel = "Rate limited",
        )

        ErrorKind.QuotaExhausted -> ErrorCopy(
            title = "You're out of credit",
            explanation = "$provider says this key has no quota left, or no billing set up. Add " +
                "credit on their website, then come back and try again.",
            fix = ErrorFix.None,
            fixLabel = null,
            shortLabel = "Out of credit",
        )

        ErrorKind.ModelUnavailable -> ErrorCopy(
            title = "That model isn't available",
            explanation = "$provider doesn't offer this model on your key any more. Pick a " +
                "different one and carry on.",
            fix = ErrorFix.PickAnotherModel,
            fixLabel = "Choose a model",
            shortLabel = "Model unavailable",
        )

        ErrorKind.ContextTooLong -> ErrorCopy(
            title = "This conversation is too long",
            explanation = "The chat has outgrown what this model can hold in mind at once. Start " +
                "a new chat, or switch to a model with more room.",
            fix = ErrorFix.StartNewChat,
            fixLabel = "New chat",
            shortLabel = "Chat too long",
        )

        ErrorKind.BadRequest -> ErrorCopy(
            title = "$provider rejected the request",
            explanation = "Something about this request wasn't accepted. Trying a different model " +
                "usually clears it.",
            fix = ErrorFix.PickAnotherModel,
            fixLabel = "Choose a model",
            shortLabel = "Request rejected",
        )

        ErrorKind.ProviderOutage -> ErrorCopy(
            title = "$provider is having trouble",
            explanation = "The problem is on their side, not yours. Try again in a minute.",
            fix = ErrorFix.Retry,
            fixLabel = "Try again",
            shortLabel = "Provider down",
        )

        ErrorKind.Offline -> ErrorCopy(
            title = "No internet connection",
            explanation = "Gecko couldn't reach $provider. Check your Wi-Fi or mobile data, then " +
                "try again.",
            fix = ErrorFix.Retry,
            fixLabel = "Try again",
            shortLabel = "Offline",
        )

        ErrorKind.SafetyBlocked -> ErrorCopy(
            title = "The model wouldn't answer that",
            explanation = "$provider's safety filters blocked this reply. Rephrasing the question " +
                "usually works, or you can try another model.",
            fix = ErrorFix.PickAnotherModel,
            fixLabel = "Choose a model",
            shortLabel = "Blocked",
        )

        ErrorKind.Truncated -> ErrorCopy(
            title = "The answer was cut off",
            explanation = "The model reached its length limit mid-answer. Ask it to continue, or " +
                "pick a model with more room to write.",
            fix = ErrorFix.None,
            fixLabel = null,
            shortLabel = "Cut off",
        )

        ErrorKind.EmptyResponse -> ErrorCopy(
            title = "The model replied with nothing",
            explanation = "$provider finished without sending any text. Trying again often works; " +
                "if it keeps happening, another model will.",
            fix = ErrorFix.Retry,
            fixLabel = "Try again",
            shortLabel = "Empty reply",
        )

        ErrorKind.KeyRemoved -> ErrorCopy(
            title = "This API key was removed",
            explanation = "The key this chat was using is no longer saved. Pick another one to " +
                "carry on.",
            fix = ErrorFix.OpenProviderKey,
            fixLabel = "Open settings",
            shortLabel = "Key removed",
        )

        ErrorKind.Unknown -> ErrorCopy(
            title = "Something went wrong",
            explanation = "Gecko couldn't finish this request. The details below may explain why.",
            fix = ErrorFix.Retry,
            fixLabel = "Try again",
            shortLabel = "Needs attention",
        )
    }
}
