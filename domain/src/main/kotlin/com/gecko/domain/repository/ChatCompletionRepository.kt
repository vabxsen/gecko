package com.gecko.domain.repository

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.provider.ModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * The only domain-layer entry point into a vendor's API. Resolves the saved config's provider
 * type and decrypted key, then builds the right [com.gecko.core.provider.api.AiProvider] under
 * the hood - callers never see vendor-specific types. Keyed by the saved config's id, since a
 * provider type can have multiple saved keys.
 */
interface ChatCompletionRepository {
    suspend fun sendMessage(
        configId: String,
        modelId: String,
        history: List<ChatMessage>,
        stream: Boolean,
    ): Flow<ChatEvent>

    suspend fun testConnection(configId: String): Result<Unit>
    suspend fun fetchModels(configId: String): Result<List<ModelInfo>>
}
