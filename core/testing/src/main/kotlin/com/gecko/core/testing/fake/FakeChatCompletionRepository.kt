package com.gecko.core.testing.fake

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.FinishReason
import com.gecko.core.model.provider.ModelInfo
import com.gecko.domain.repository.ChatCompletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeChatCompletionRepository(
    private val flowBuilder: () -> Flow<ChatEvent> = {
        flowOf(ChatEvent.Started(), ChatEvent.ContentDelta("Hi"), ChatEvent.Completed(FinishReason.STOP, null))
    },
    private val testConnectionResult: Result<Unit> = Result.success(Unit),
    private val fetchModelsResult: Result<List<ModelInfo>> = Result.success(emptyList()),
) : ChatCompletionRepository {

    var lastRequest: Triple<String, String, List<ChatMessage>>? = null
        private set

    override suspend fun sendMessage(
        configId: String,
        modelId: String,
        history: List<ChatMessage>,
        stream: Boolean,
    ): Flow<ChatEvent> {
        lastRequest = Triple(configId, modelId, history)
        return flowBuilder()
    }

    override suspend fun testConnection(configId: String): Result<Unit> = testConnectionResult

    override suspend fun fetchModels(configId: String): Result<List<ModelInfo>> = fetchModelsResult
}
