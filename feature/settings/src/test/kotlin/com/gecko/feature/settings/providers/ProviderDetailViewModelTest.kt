package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeSecureKeyRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import com.gecko.domain.usecase.TestProviderConnectionUseCase
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

    @Test
    fun savedKeyLoadsBeforeTheEditorIsEnabledAndCanBeReplaced() = runTest {
        val providers = FakeProviderConfigRepository()
        val keys = FakeSecureKeyRepository()
        val chat = FakeChatCompletionRepository()
        val id = providers.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        keys.saveApiKey(id, "old-key")
        val viewModel = ProviderDetailViewModel(
            SavedStateHandle(mapOf("configId" to id)), providers, keys,
            SaveProviderApiKeyUseCase(keys, providers), FakeUserPreferencesRepository(),
            TestProviderConnectionUseCase(chat, providers), RefreshProviderModelsUseCase(chat, providers),
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
    }
}
