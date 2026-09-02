package com.gecko.core.provider.api

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull

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

    /**
     * Sends the smallest real chat request this provider will accept and reports the failure, if
     * any. [testConnection] alone can't be trusted to validate a key: every implementation of it
     * is a `/models` call, and some catalogs — NVIDIA NIM's in particular — are served without
     * authentication, so a completely fake key came back "Connected". Only an actual completion
     * proves the key works.
     *
     * Returns `null` when the probe succeeded.
     */
    suspend fun probeChat(model: String): ChatEvent.Error? =
        sendMessage(listOf(PROBE_MESSAGE), model, stream = false)
            .filterIsInstance<ChatEvent.Error>()
            .firstOrNull()
}

private val PROBE_MESSAGE = ChatMessage(
    id = "connection-probe",
    conversationId = "connection-probe",
    role = MessageRole.USER,
    content = "Hi",
    createdAt = Instant.EPOCH,
    status = MessageStatus.COMPLETE,
)
