package com.gecko.feature.settings.providers

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeChatCompletionRepository
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.fake.FakeSecureKeyRepository
import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import com.gecko.domain.usecase.ConnectProviderUseCase
import com.gecko.domain.usecase.SaveProviderApiKeyUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddProviderViewModelTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val model = ModelInfo(ProviderId.OPENAI, "gpt-4o-mini", "GPT", 128_000, true, true)

    private fun viewModel(
        providers: FakeProviderConfigRepository = FakeProviderConfigRepository(),
        preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        chat: FakeChatCompletionRepository = FakeChatCompletionRepository(fetchModelsResult = Result.success(listOf(model))),
    ) = AddProviderViewModel(providers, SaveProviderApiKeyUseCase(FakeSecureKeyRepository(), providers),
        ConnectProviderUseCase(chat, providers, preferences))

    @Test fun recognizableKeysSelectTheirProviderLocally() {
        mapOf("sk-ant-api03-example" to "Anthropic", "sk-or-v1-example" to "OpenRouter",
            "nvapi-example" to "NVIDIA NIM", "AIzaExample" to "Google Gemini", "sk-proj-example" to "OpenAI",
            "sk-svcacct-example" to "OpenAI").forEach { (key, label) ->
            val vm = viewModel()
            vm.updateApiKey(" $key ")
            assertEquals(label, vm.uiState.value.providerLabel)
            assertTrue(vm.uiState.value.canSave)
        }
    }

    @Test fun ambiguousKeysRequireOneProviderChoiceWithoutGuessing() {
        val vm = viewModel()
        vm.updateApiKey("sk-generic")
        assertNull(vm.uiState.value.selectedProviderId)
        assertFalse(vm.uiState.value.canSave)
        vm.selectOption(ADD_PROVIDER_OPTIONS.first { it.label == "DeepSeek" })
        assertTrue(vm.uiState.value.canSave)
        assertEquals("https://api.deepseek.com/v1", vm.uiState.value.baseUrlOverride)
    }

    @Test fun replacingAnAutomaticallyDetectedKeyUpdatesItsProvider() {
        val vm = viewModel()
        vm.updateApiKey("nvapi-example")
        vm.updateApiKey("AIzaExample")
        assertEquals(ProviderId.GOOGLE, vm.uiState.value.selectedProviderId)
        assertEquals("", vm.uiState.value.baseUrlOverride)
        vm.updateApiKey("")
        assertNull(vm.uiState.value.selectedProviderId)
    }

    @Test fun explicitProviderChoiceIsPreservedWhileEditingTheKey() {
        val vm = viewModel()
        vm.selectOption(ADD_PROVIDER_OPTIONS.first { it.label == "Kimi (Moonshot AI)" })
        vm.updateApiKey("sk-proj-custom-format")
        assertEquals("Kimi (Moonshot AI)", vm.uiState.value.providerLabel)
        assertEquals("https://api.moonshot.ai/v1", vm.uiState.value.baseUrlOverride)
    }

    @Test fun pasteAndConnectSetsAVerifiedModelAndNavigatesOnce() = runTest {
        val providers = FakeProviderConfigRepository()
        val preferences = FakeUserPreferencesRepository(UserPreferences(defaultProviderConfigId = "old", defaultModelId = "old-model"))
        val vm = viewModel(providers, preferences)
        vm.updateApiKey("sk-proj-example")
        var callbacks = 0
        vm.save { callbacks++ }
        vm.save { callbacks++ }
        advanceUntilIdle()
        vm.save { callbacks++ }
        val config = providers.observeAll().first().single()
        assertEquals(1, callbacks)
        assertTrue(config.enabled)
        assertEquals(config.id, preferences.userPreferences.value.defaultProviderConfigId)
        assertEquals(model.modelId, preferences.userPreferences.value.defaultModelId)
        assertEquals("", vm.uiState.value.apiKey)
    }

    @Test fun failedConnectionStaysOnFormKeepsDraftAndLeavesExistingDefaultAlone() = runTest {
        val providers = FakeProviderConfigRepository()
        val preferences = FakeUserPreferencesRepository(UserPreferences(defaultProviderConfigId = "old", defaultModelId = "old-model"))
        val chat = FakeChatCompletionRepository(
            fetchModelsResult = Result.success(listOf(model)),
            flowBuilder = { flowOf(ChatEvent.Error(GeckoError(ErrorKind.QuotaExhausted))) },
        )
        val vm = viewModel(providers, preferences, chat)
        vm.updateApiKey("sk-proj-example")
        var callbacks = 0
        vm.save { callbacks++ }
        advanceUntilIdle()
        vm.save { callbacks++ }
        advanceUntilIdle()
        assertEquals(0, callbacks)
        assertEquals(ErrorKind.QuotaExhausted, vm.uiState.value.error?.kind)
        assertEquals("sk-proj-example", vm.uiState.value.apiKey)
        assertTrue(providers.observeAll().first().isEmpty())
        assertEquals("old", preferences.userPreferences.value.defaultProviderConfigId)
        assertFalse(vm.uiState.value.isSaving)
    }
}
