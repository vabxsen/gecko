package com.gecko.core.provider.api

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * A single AI vendor integration. Implementations are provider-agnostic from the caller's
 * point of view: the UI and chat logic only ever see [ChatMessage]/[ChatEvent], never a
 * vendor's wire format.
 */
interface AiProvider {
    val id: ProviderId

    suspend fun sendMessage(messages: List<ChatMessage>, model: String, stream: Boolean): Flow<ChatEvent>

    suspend fun listModels(): Result<List<ModelInfo>>

    suspend fun testConnection(): Result<Unit>
}
