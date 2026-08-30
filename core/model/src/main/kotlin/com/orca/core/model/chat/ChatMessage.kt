package com.orca.core.model.chat

import com.orca.core.model.provider.ProviderId
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
    val errorMessage: String? = null,
    /** Base64-encoded JPEG the user attached. Displayed inline; not sent to the AI provider. */
    val attachmentImageBase64: String? = null,
)
