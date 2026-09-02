package com.gecko.core.model.chat

import com.gecko.core.model.error.GeckoError

sealed interface ChatEvent {
    data class Started(val providerMessageId: String? = null) : ChatEvent
    data class ContentDelta(val text: String) : ChatEvent
    data class ImageDelta(val base64: String, val mimeType: String) : ChatEvent
    data class Completed(val finishReason: FinishReason, val usage: TokenUsage?) : ChatEvent

    /**
     * Everything about the failure lives in [error]. This used to also carry `message`,
     * `isRetryable` and `httpStatusCode` as separate fields, and every consumer kept only the
     * string — the other two were computed correctly by every provider and then dropped one layer
     * later. Holding a single value is what stops that happening again.
     */
    data class Error(val error: GeckoError, val cause: Throwable? = null) : ChatEvent
}
