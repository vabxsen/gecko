package com.gecko.feature.settings.modelprefs

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ModelPreferencesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onlyEnabledProvidersWithKeysAreOffered() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val viewModel = ModelPreferencesViewModel(providerConfigRepository, userPreferencesRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.enabledProviders.isEmpty())

        val configId = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        providerConfigRepository.setEnabled(configId, true)
        providerConfigRepository.setHasApiKey(configId, true)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.enabledProviders.size)
    }

    @Test
    fun modelsForDefaultProviderAreObserved() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val viewModel = ModelPreferencesViewModel(providerConfigRepository, userPreferencesRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val configId = providerConfigRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        userPreferencesRepository.setDefaultProviderConfig(configId)
        providerConfigRepository.saveModels(
            configId,
            listOf(ModelInfo(ProviderId.OPENAI, "gpt-4o", "GPT-4o", 128_000, true, true)),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.modelsForDefaultProvider.size)
    }
}
