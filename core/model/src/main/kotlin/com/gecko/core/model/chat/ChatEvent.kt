package com.gecko.core.model.chat

sealed interface ChatEvent {
    data class Started(val providerMessageId: String? = null) : ChatEvent
    data class ContentDelta(val text: String) : ChatEvent
    data class ImageDelta(val base64: String, val mimeType: String) : ChatEvent
    data class Completed(val finishReason: FinishReason, val usage: TokenUsage?) : ChatEvent
    data class Error(val message: String, val cause: Throwable?, val isRetryable: Boolean) : ChatEvent
}
