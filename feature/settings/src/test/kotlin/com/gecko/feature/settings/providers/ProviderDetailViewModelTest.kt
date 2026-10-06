package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeSecureKeyRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import com.gecko.domain.usecase.ConnectProviderUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProviderDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test fun diagnosticsDoNotActivateADisabledConnectionOrSwitchTheUsersSelection() = runTest {
        val providers = FakeProviderConfigRepository()
        val keys = FakeSecureKeyRepository()
        val prefs = FakeUserPreferencesRepository(com.gecko.core.model.preferences.UserPreferences(
            defaultProviderConfigId = "other", defaultModelId = "other-model"))
        val model = com.gecko.core.model.provider.ModelInfo(ProviderId.OPENAI, "gpt-4o-mini", "GPT", 128000, true, true)
        val chat = FakeChatCompletionRepository(fetchModelsResult = Result.success(listOf(model)))
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        keys.saveApiKey(id, "test-key")
        providers.setEnabled(id, false)
        val vm = ProviderDetailViewModel(SavedStateHandle(mapOf("configId" to id)), providers, keys,
            SaveProviderApiKeyUseCase(keys, providers), ConnectProviderUseCase(chat, providers, prefs))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.runDiagnostics()
        advanceUntilIdle()
        assertEquals(ConnectionStatus.Success, vm.uiState.value.connectionStatus)
        assertFalse(providers.currentConfig(id)!!.enabled)
        assertEquals("other", prefs.userPreferences.value.defaultProviderConfigId)
        assertTrue(vm.uiState.value.lastCheckedAt != null)
    }

    @Test
    fun replacingAKeyDoesNotReuseItsOldModelWhenVerificationFails() = runTest {
        val providers = FakeProviderConfigRepository()
        val keys = FakeSecureKeyRepository()
        val prefs = FakeUserPreferencesRepository()
        val model = com.gecko.core.model.provider.ModelInfo(ProviderId.OPENAI, "gpt-4o-mini", "GPT", 128000, true, true)
        val chat = FakeChatCompletionRepository(
            fetchModelsResult = Result.success(listOf(model)),
            flowBuilder = { kotlinx.coroutines.flow.flowOf(com.gecko.core.model.chat.ChatEvent.Error(
                com.gecko.core.model.error.GeckoError(com.gecko.core.model.error.ErrorKind.InvalidApiKey))) },
        )
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        keys.saveApiKey(id, "old-key")
        providers.setVerifiedModel(id, "old-model")
        val viewModel = ProviderDetailViewModel(SavedStateHandle(mapOf("configId" to id)), providers, keys,
            SaveProviderApiKeyUseCase(keys, providers), ConnectProviderUseCase(chat, providers, prefs))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.saveApiKey("replacement-key")
        advanceUntilIdle()
        assertEquals(null, providers.currentConfig(id)?.verifiedModelId)
        assertTrue(viewModel.uiState.value.connectionStatus is ConnectionStatus.Failure)
        assertFalse(viewModel.uiState.value.isSavingKey)
        assertTrue(viewModel.uiState.value.saveKeyErrorMessage != null)
        assertTrue(viewModel.uiState.value.lastCheckedAt != null)
        assertTrue(viewModel.uiState.value.checkDurationMs!! >= 0)
    }

    @Test
    fun savedKeyLoadsBeforeTheEditorIsEnabledAndCanBeReplaced() = runTest {
        val providers = FakeProviderConfigRepository()
        val keys = FakeSecureKeyRepository()
        val model = com.gecko.core.model.provider.ModelInfo(ProviderId.OPENAI, "gpt-4o-mini", "GPT", 128000, true, true)
        val chat = FakeChatCompletionRepository(fetchModelsResult = Result.success(listOf(model)))
        val prefs = FakeUserPreferencesRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        keys.saveApiKey(id, "old-key")
        val viewModel = ProviderDetailViewModel(
            SavedStateHandle(mapOf("configId" to id)), providers, keys,
            SaveProviderApiKeyUseCase(keys, providers),
            ConnectProviderUseCase(chat, providers, prefs),
        )
        assertTrue(viewModel.uiState.value.isLoading)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isApiKeyLoaded)
        assertEquals("old-key", viewModel.uiState.value.apiKeyValue)

        viewModel.saveApiKey(" new-key ")
        viewModel.saveApiKey("duplicate-key")
        advanceUntilIdle()

        assertEquals("new-key", keys.getApiKey(id))
        assertEquals(ConnectionStatus.Success, viewModel.uiState.value.connectionStatus)
        assertFalse(viewModel.uiState.value.isSavingKey)
        assertEquals(model.modelId, chat.lastRequest?.second)
        assertTrue(viewModel.uiState.value.lastCheckedAt != null)
        assertEquals(model.modelId, providers.currentConfig(id)?.verifiedModelId)
        assertEquals(model.modelId, prefs.userPreferences.value.defaultModelId)
    }
}
