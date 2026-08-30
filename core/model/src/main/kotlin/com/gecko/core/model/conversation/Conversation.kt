package com.gecko.core.model.conversation

import com.gecko.core.model.provider.ProviderId
import java.time.Instant

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pinned: Boolean,
    val providerId: ProviderId?,
    val modelId: String?,
)
