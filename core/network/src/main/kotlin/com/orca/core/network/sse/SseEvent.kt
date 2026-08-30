package com.orca.core.network.sse

data class SseEvent(
    val event: String?,
    val data: String,
    val id: String? = null,
)
