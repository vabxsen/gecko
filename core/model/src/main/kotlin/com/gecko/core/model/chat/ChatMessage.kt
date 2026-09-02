package com.gecko.core.model.chat

import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.provider.ProviderId
import java.time.Instant

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
    val status: MessageStatus,
    val providerId: ProviderId? = null,
    val modelId: String? = null,
    val tokenUsage: TokenUsage? = null,
    /** The provider's own wording, kept as supporting detail behind the explanation. */
    val errorMessage: String? = null,
    /**
     * What went wrong, persisted so a failure is still explainable when the conversation is
     * reopened days later — the transient dialog that first reported it is long gone by then.
     */
    val errorKind: ErrorKind? = null,
    /** Base64-encoded JPEG the user attached. Displayed inline and sent to the AI provider. */
    val attachmentImageBase64: String? = null,
    /** Base64-encoded image an image-output model (e.g. Gemini's "-image" models) generated. */
    val generatedImageBase64: String? = null,
)
