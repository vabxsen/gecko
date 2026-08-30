package com.gecko.feature.settings.providers

import com.gecko.core.model.provider.ProviderId
import com.gecko.core.testing.fake.FakeProviderConfigRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AiProvidersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observesAddedProviders() = runTest {
        val repository = FakeProviderConfigRepository()
        val viewModel = AiProvidersViewModel(repository)
        backgroundScope.launch { viewModel.providerConfigs.collect {} }
        advanceUntilIdle()

        assertEquals(0, viewModel.providerConfigs.value.size)

        repository.addProvider(ProviderId.OPENAI, "OpenAI")
        advanceUntilIdle()

        assertEquals(1, viewModel.providerConfigs.value.size)
    }

    @Test
    fun settingEnabledUpdatesTheRightProvider() = runTest {
        val repository = FakeProviderConfigRepository()
        val openAiId = repository.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val anthropicId = repository.addProvider(ProviderId.ANTHROPIC, "Anthropic").getOrThrow()
        val viewModel = AiProvidersViewModel(repository)
        backgroundScope.launch { viewModel.providerConfigs.collect {} }
        advanceUntilIdle()

        viewModel.setEnabled(anthropicId, false)
        advanceUntilIdle()

        val configs = viewModel.providerConfigs.value
        assertTrue(configs.first { it.id == openAiId }.enabled)
        assertTrue(configs.none { it.id == anthropicId && it.enabled })
    }
}
