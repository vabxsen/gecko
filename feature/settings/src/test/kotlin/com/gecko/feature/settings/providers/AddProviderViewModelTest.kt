package com.gecko.feature.settings.providers

import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeSecureKeyRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddProviderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(providerConfigRepository: FakeProviderConfigRepository) = AddProviderViewModel(
        providerConfigRepository,
        SaveProviderApiKeyUseCase(FakeSecureKeyRepository(), providerConfigRepository),
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
}
