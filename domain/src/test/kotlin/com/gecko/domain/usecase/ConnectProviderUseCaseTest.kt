package com.gecko.domain.usecase

import com.gecko.core.model.chat.*
import com.gecko.core.model.error.*
import com.gecko.core.model.provider.*
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.model.connectionCandidates
import com.gecko.domain.usecase.fakes.FakeProviderConfigRepository
import com.gecko.domain.usecase.fakes.FakeUserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConnectProviderUseCaseTest {
    private val fast = ModelInfo(ProviderId.OPENAI, "gpt-4o-mini", "Fast", 128_000, true, true)
    private val alternative = fast.copy(modelId = "gpt-4o")

    @Test fun automaticCandidatesPreferFreeOpenRouterOptionsAndRemainBounded() {
        val models = (1..20).map { fast.copy(providerId = ProviderId.OPENROUTER, modelId = "vendor/model-$it:free") }
        val candidates = (listOf(fast.copy(providerId = ProviderId.OPENROUTER)) + models)
            .connectionCandidates(ProviderId.OPENROUTER, null)
        assertEquals(5, candidates.size)
        assertTrue(candidates.first().modelId.endsWith(":free"))
        assertEquals(candidates.size, candidates.map { it.modelId }.distinct().size)
    }

    private class Chat(private val models: List<ModelInfo>, private val errors: Map<String, ErrorKind> = emptyMap()) : ChatCompletionRepository {
        val requested = mutableListOf<String>()
        var fetches = 0
        var cancel = false
        var hang = false
        var empty = false
        override suspend fun fetchModels(configId: String): Result<List<ModelInfo>> { fetches++; return Result.success(models) }
        override suspend fun testConnection(configId: String) = error("Setup must test its chosen model")
        override suspend fun sendMessage(configId: String, modelId: String, history: List<ChatMessage>, stream: Boolean): Flow<ChatEvent> {
            assertTrue(stream)
            assertEquals(1, history.size)
            assertEquals(MessageRole.USER, history.single().role)
            requested += modelId
            if (cancel) throw CancellationException("User left setup")
            if (hang) return flow { awaitCancellation() }
            errors[modelId]?.let { return flowOf(ChatEvent.Error(GeckoError(it))) }
            return if (empty) flowOf(ChatEvent.Completed(FinishReason.STOP, null))
                else flowOf(ChatEvent.ContentDelta("OK"), ChatEvent.Completed(FinishReason.STOP, null))
        }
    }

    @Test fun unavailableDefaultFallsBackAndSavesTheModelThatActuallyReplied() = runTest {
        val providers = FakeProviderConfigRepository()
        val prefs = FakeUserPreferencesRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chat = Chat(listOf(fast, alternative), mapOf(fast.modelId to ErrorKind.QuotaExhausted))
        val result = ConnectProviderUseCase(chat, providers, prefs)(id).getOrThrow()
        assertEquals(alternative.modelId, result.modelId)
        assertEquals(listOf(fast.modelId, alternative.modelId), chat.requested)
        assertEquals(1, chat.fetches)
        assertEquals(alternative.modelId, prefs.userPreferences.value.defaultModelId)
        assertEquals(ConnectionStatus.Success, providers.currentStatus(id))
    }

    @Test fun invalidKeyFailsImmediatelyWithoutTryingOtherModels() = runTest {
        val providers = FakeProviderConfigRepository()
        val prefs = FakeUserPreferencesRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chat = Chat(listOf(fast, alternative), mapOf(fast.modelId to ErrorKind.InvalidApiKey))
        val error = ConnectProviderUseCase(chat, providers, prefs)(id).exceptionOrNull() as GeckoException
        assertEquals(ErrorKind.InvalidApiKey, error.error.kind)
        assertEquals(listOf(fast.modelId), chat.requested)
        assertNull(prefs.userPreferences.value.defaultModelId)
    }

    @Test fun catalogsContainingOnlyNonChatModelsCannotPassSetup() = runTest {
        val providers = FakeProviderConfigRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chat = Chat(listOf(fast.copy(modelId = "text-embedding-3-small")))
        val error = ConnectProviderUseCase(chat, providers, FakeUserPreferencesRepository())(id).exceptionOrNull() as GeckoException
        assertEquals(ErrorKind.ModelUnavailable, error.error.kind)
        assertTrue(chat.requested.isEmpty())
    }

    @Test fun emptyRepliesNeverLeadToConnected() = runTest {
        val providers = FakeProviderConfigRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chat = Chat(listOf(fast)).apply { empty = true }
        val error = ConnectProviderUseCase(chat, providers, FakeUserPreferencesRepository())(id).exceptionOrNull() as GeckoException
        assertEquals(ErrorKind.EmptyResponse, error.error.kind)
    }

    @Test fun stalledProviderHasABoundedWait() = runTest {
        val providers = FakeProviderConfigRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chat = Chat(listOf(fast)).apply { hang = true }
        val result = ConnectProviderUseCase(chat, providers, FakeUserPreferencesRepository())(id)
        assertEquals(ErrorKind.Offline, (result.exceptionOrNull() as GeckoException).error.kind)
        assertEquals(45_000, testScheduler.currentTime)
    }

    @Test fun cancellationIsNotReportedAsAConnectionFailure() = runTest {
        val providers = FakeProviderConfigRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chat = Chat(listOf(fast)).apply { cancel = true }
        val result = runCatching { ConnectProviderUseCase(chat, providers, FakeUserPreferencesRepository())(id) }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
