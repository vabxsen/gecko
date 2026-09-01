package com.gecko.feature.settings.providers

import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeSecureKeyRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import com.gecko.domain.usecase.TestProviderConnectionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddProviderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        providerConfigRepository: FakeProviderConfigRepository,
        userPreferencesRepository: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        chatCompletionRepository: FakeChatCompletionRepository = FakeChatCompletionRepository(),
    ) = AddProviderViewModel(
        providerConfigRepository,
        userPreferencesRepository,
        SaveProviderApiKeyUseCase(FakeSecureKeyRepository(), providerConfigRepository),
        TestProviderConnectionUseCase(chatCompletionRepository, providerConfigRepository),
        RefreshProviderModelsUseCase(chatCompletionRepository, providerConfigRepository),
    )

    private fun option(label: String) = ADD_PROVIDER_OPTIONS.first { it.label == label }

    @Test
    fun allFourProtocolsAndAllThreeCompatibleEndpointsAreAlwaysOfferedTogether() {
        val labels = ADD_PROVIDER_OPTIONS.map { it.label }

        assertEquals(
            listOf("OpenAI", "Anthropic", "Google Gemini", "OpenRouter", "DeepSeek", "Kimi (Moonshot AI)", "NVIDIA NIM"),
            labels,
        )
    }

    @Test
    fun selectingACompatibleEndpointFillsProviderTypeBaseUrlAndLabelInOneStep() {
        val viewModel = viewModel(FakeProviderConfigRepository())

        viewModel.selectOption(option("DeepSeek"))

        assertEquals(ProviderId.OPENAI, viewModel.uiState.value.selectedProviderId)
        assertEquals("https://api.deepseek.com/v1", viewModel.uiState.value.baseUrlOverride)
        assertEquals("DeepSeek", viewModel.uiState.value.label)
    }

    @Test
    fun savingACompatibleEndpointPersistsTheBaseUrlOverride() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val viewModel = viewModel(providerConfigRepository)

        viewModel.selectOption(option("Kimi (Moonshot AI)"))
        viewModel.updateApiKey("sk-test")
        viewModel.save(onSaved = {})
        advanceUntilIdle()

        val config = providerConfigRepository.observeAll().first().single()
        assertEquals(ProviderId.OPENAI, config.providerId)
        assertEquals("https://api.moonshot.ai/v1", config.baseUrlOverride)
    }

    @Test
    fun switchingToAPlainProtocolClearsAPreviouslyPickedBaseUrl() {
        val viewModel = viewModel(FakeProviderConfigRepository())

        viewModel.selectOption(option("NVIDIA NIM"))
        viewModel.selectOption(option("Anthropic"))

        assertEquals("", viewModel.uiState.value.baseUrlOverride)
    }

    @Test
    fun choosingThePlainOpenAiOptionClearsAPreviouslyPickedOverride() {
        val viewModel = viewModel(FakeProviderConfigRepository())

        viewModel.selectOption(option("DeepSeek"))
        viewModel.selectOption(option("OpenAI"))

        assertEquals("", viewModel.uiState.value.baseUrlOverride)
    }

    @Test
    fun savingAWorkingFirstKeyAdoptsItsBestChatModelAsTheDefault() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val chatCompletionRepository = FakeChatCompletionRepository(
            fetchModelsResult = Result.success(
                listOf(
                    ModelInfo(ProviderId.OPENAI, "text-embedding-3-small", "embed", 8_000, false, false),
                    ModelInfo(ProviderId.OPENAI, "gpt-4o", "gpt-4o", 128_000, true, true),
                ),
            ),
        )
        val viewModel = viewModel(providerConfigRepository, userPreferencesRepository, chatCompletionRepository)

        viewModel.selectOption(option("OpenAI"))
        viewModel.updateApiKey("sk-test")
        viewModel.save(onSaved = {})
        advanceUntilIdle()

        val configId = providerConfigRepository.observeAll().first().single().id
        val prefs = userPreferencesRepository.userPreferences.first()
        assertEquals(configId, prefs.defaultProviderConfigId)
        assertEquals("gpt-4o", prefs.defaultModelId)
    }

    @Test
    fun savingAKeyNeverOverridesAnAlreadyChosenDefault() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository(
            UserPreferences(defaultProviderConfigId = "existing-config", defaultModelId = "existing-model"),
        )
        val chatCompletionRepository = FakeChatCompletionRepository(
            fetchModelsResult = Result.success(listOf(ModelInfo(ProviderId.OPENAI, "gpt-4o", "gpt-4o", 128_000, true, true))),
        )
        val viewModel = viewModel(providerConfigRepository, userPreferencesRepository, chatCompletionRepository)

        viewModel.selectOption(option("OpenAI"))
        viewModel.updateApiKey("sk-test")
        viewModel.save(onSaved = {})
        advanceUntilIdle()

        val prefs = userPreferencesRepository.userPreferences.first()
        assertEquals("existing-config", prefs.defaultProviderConfigId)
        assertEquals("existing-model", prefs.defaultModelId)
    }

    @Test
    fun savingAKeyThatFailsTheConnectionTestRollsBackAndSurfacesTheFailure() = runTest {
        val providerConfigRepository = FakeProviderConfigRepository()
        val chatCompletionRepository = FakeChatCompletionRepository(
            testConnectionResult = Result.failure(IllegalStateException("Invalid API key")),
        )
        val viewModel = viewModel(providerConfigRepository, chatCompletionRepository = chatCompletionRepository)
        var saved = false

        viewModel.selectOption(option("OpenAI"))
        viewModel.updateApiKey("sk-bad")
        viewModel.save(onSaved = { saved = true })
        advanceUntilIdle()

        assertTrue(providerConfigRepository.observeAll().first().isEmpty())
        assertEquals("Invalid API key", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isSaving)
        assertEquals(false, saved)
    }
}
