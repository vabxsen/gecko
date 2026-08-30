package com.orca.domain.repository

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * The only domain-layer entry point into a vendor's API. Resolves the decrypted key and
 * builds the right [com.orca.core.provider.api.AiProvider] under the hood - callers never
 * see vendor-specific types.
 */
interface ChatCompletionRepository {
    suspend fun sendMessage(
        providerId: ProviderId,
        modelId: String,
        history: List<ChatMessage>,
        stream: Boolean,
    ): Flow<ChatEvent>

    suspend fun testConnection(providerId: ProviderId): Result<Unit>
    suspend fun fetchModels(providerId: ProviderId): Result<List<ModelInfo>>
}
