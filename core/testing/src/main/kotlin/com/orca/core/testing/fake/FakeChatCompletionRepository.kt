package com.orca.core.testing.fake

import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.chat.FinishReason
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ChatCompletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeChatCompletionRepository(
    private val flowBuilder: () -> Flow<ChatEvent> = {
        flowOf(ChatEvent.Started(), ChatEvent.ContentDelta("Hi"), ChatEvent.Completed(FinishReason.STOP, null))
    },
    private val testConnectionResult: Result<Unit> = Result.success(Unit),
    private val fetchModelsResult: Result<List<ModelInfo>> = Result.success(emptyList()),
) : ChatCompletionRepository {

    var lastRequest: Triple<ProviderId, String, List<ChatMessage>>? = null
        private set

    override suspend fun sendMessage(
        providerId: ProviderId,
        modelId: String,
        history: List<ChatMessage>,
        stream: Boolean,
    ): Flow<ChatEvent> {
        lastRequest = Triple(providerId, modelId, history)
        return flowBuilder()
    }

    override suspend fun testConnection(providerId: ProviderId): Result<Unit> = testConnectionResult

    override suspend fun fetchModels(providerId: ProviderId): Result<List<ModelInfo>> = fetchModelsResult
}
