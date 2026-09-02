package com.gecko.feature.settings.providers

import androidx.lifecycle.SavedStateHandle
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
import com.gecko.feature.settings.navigation.ModelSelectionRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Replaces ModelPreferencesViewModelTest. The behaviour it pinned — that a model choice reaches the
 * preferences chat reads — still has to hold; it just happens on one screen now instead of two.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ModelSelectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val configRepository = FakeProviderConfigRepository()
    private val preferencesRepository = FakeUserPreferencesRepository()

    private fun model(id: String) = ModelInfo(
        providerId = ProviderId.GOOGLE,
        modelId = id,
        displayName = id,
        contextWindowTokens = 1_000_000,
        supportsStreaming = true,
        supportsImages = true,
    )

    private fun buildViewModel(configId: String) = ModelSelectionViewModel(
        savedStateHandle = SavedStateHandle(mapOf("configId" to configId)),
        providerConfigRepository = configRepository,
        userPreferencesRepository = preferencesRepository,
        // The catalog is seeded directly in each test, so the auto-fetch has nothing to do.
        refreshProviderModelsUseCase = RefreshProviderModelsUseCase(FakeChatCompletionRepository(), configRepository),
    )

    @Test
    fun pickingAModelSetsBothTheKeyAndTheModelInOneStep() = runTest {
        // The old AI Providers picker wrote a per-config field chat never read, so choosing a model
        // there changed nothing the user could see. This is the behaviour that has to hold.
        val id = configRepository.addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        configRepository.saveModels(id, listOf(model("gemini-3.6-flash")))
        val viewModel = buildViewModel(id)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectModel("gemini-3.6-flash")
        advanceUntilIdle()

        val prefs = preferencesRepository.userPreferences.first()
        assertEquals(id, prefs.defaultProviderConfigId)
        assertEquals("gemini-3.6-flash", prefs.defaultModelId)
    }

    @Test
    fun anotherKeysListDoesNotShowAModelBorrowedFromTheOneInUse() = runTest {
        val inUse = configRepository.addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        val other = configRepository.addProvider(ProviderId.ANTHROPIC, "Claude").getOrThrow()
        configRepository.saveModels(other, listOf(model("claude-sonnet-4-5")))
        preferencesRepository.setDefaultProviderConfig(inUse)
        preferencesRepository.setDefaultModel("gemini-3.6-flash")

        val viewModel = buildViewModel(other)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedModelId)
    }

    @Test
    fun theModelInUseIsTickedOnItsOwnKeysList() = runTest {
        val id = configRepository.addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        configRepository.saveModels(id, listOf(model("gemini-3.6-flash")))
        preferencesRepository.setDefaultProviderConfig(id)
        preferencesRepository.setDefaultModel("gemini-3.6-flash")

        val viewModel = buildViewModel(id)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("gemini-3.6-flash", viewModel.uiState.value.selectedModelId)
    }
}
