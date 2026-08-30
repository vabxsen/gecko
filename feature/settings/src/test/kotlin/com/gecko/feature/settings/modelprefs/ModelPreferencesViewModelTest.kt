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

        providerConfigRepository.setEnabled(ProviderId.OPENAI, true)
        providerConfigRepository.setHasApiKey(ProviderId.OPENAI, true)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.enabledProviders.size)
    }

    @Test
    fun selectingDefaultProviderClearsPreviousDefaultModel() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val viewModel = ModelPreferencesViewModel(providerConfigRepository, userPreferencesRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        userPreferencesRepository.setDefaultProvider(ProviderId.OPENAI)
        userPreferencesRepository.setDefaultModel("gpt-4o")
        advanceUntilIdle()

        viewModel.selectDefaultProvider(ProviderId.ANTHROPIC)
        advanceUntilIdle()

        val prefs = userPreferencesRepository.userPreferences.value
        assertEquals(ProviderId.ANTHROPIC, prefs.defaultProviderId)
        assertEquals(null, prefs.defaultModelId)
    }

    @Test
    fun modelsForDefaultProviderAreObserved() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val viewModel = ModelPreferencesViewModel(providerConfigRepository, userPreferencesRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        userPreferencesRepository.setDefaultProvider(ProviderId.OPENAI)
        providerConfigRepository.saveModels(
            ProviderId.OPENAI,
            listOf(ModelInfo(ProviderId.OPENAI, "gpt-4o", "GPT-4o", 128_000, true, true)),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.modelsForDefaultProvider.size)

        viewModel.selectDefaultModel("gpt-4o")
        advanceUntilIdle()

        assertEquals("gpt-4o", userPreferencesRepository.userPreferences.value.defaultModelId)
    }
}
