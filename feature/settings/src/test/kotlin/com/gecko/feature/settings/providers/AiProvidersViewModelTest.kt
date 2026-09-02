package com.gecko.feature.settings.providers

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AiProvidersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val configRepository = FakeProviderConfigRepository()
    private val preferencesRepository = FakeUserPreferencesRepository()

    private fun buildViewModel() = AiProvidersViewModel(configRepository, preferencesRepository)

    @Test
    fun observesAddedProviders() = runTest {
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.size)

        configRepository.addProvider(ProviderId.OPENAI, "OpenAI")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.size)
    }

    @Test
    fun settingEnabledUpdatesTheRightProvider() = runTest {
        val openAiId = configRepository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val anthropicId = configRepository.addProvider(ProviderId.ANTHROPIC, "Anthropic").getOrThrow()
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setEnabled(anthropicId, false)
        advanceUntilIdle()

        val rows = viewModel.uiState.value
        assertTrue(rows.first { it.config.id == openAiId }.config.enabled)
        assertTrue(rows.none { it.config.id == anthropicId && it.config.enabled })
    }

    @Test
    fun theKeyInUseNamesItsModelSoTheListAnswersWhatSettingsUsedToNeedASecondScreenFor() = runTest {
        val id = configRepository.addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        configRepository.saveModels(
            id,
            listOf(
                ModelInfo(
                    providerId = ProviderId.GOOGLE,
                    modelId = "gemini-3.6-flash",
                    displayName = "gemini-3.6-flash",
                    contextWindowTokens = 1_000_000,
                    supportsStreaming = true,
                    supportsImages = true,
                ),
            ),
        )
        preferencesRepository.setDefaultProviderConfig(id)
        preferencesRepository.setDefaultModel("gemini-3.6-flash")

        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val row = viewModel.uiState.value.single()
        assertTrue(row.isInUse)
        // Prettified, not the raw id — the same name the chat picker shows.
        assertEquals("Gemini 3.6 Flash", row.modelLabel)
    }

    @Test
    fun aKeyThatIsNotInUseDoesNotBorrowAnotherKeysModel() = runTest {
        val inUse = configRepository.addProvider(ProviderId.GOOGLE, "Gemini").getOrThrow()
        configRepository.addProvider(ProviderId.ANTHROPIC, "Claude").getOrThrow()
        preferencesRepository.setDefaultProviderConfig(inUse)
        preferencesRepository.setDefaultModel("gemini-3.6-flash")

        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val other = viewModel.uiState.value.first { it.config.id != inUse }
        assertFalse(other.isInUse)
        assertNull(other.modelLabel)
    }
}
