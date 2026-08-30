package com.orca.feature.settings.providers

import com.orca.core.model.provider.ProviderId
import com.orca.core.testing.fake.FakeProviderConfigRepository
import com.orca.core.testing.rule.MainDispatcherRule
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
    fun observesAllFourProviders() = runTest {
        val repository = FakeProviderConfigRepository()
        val viewModel = AiProvidersViewModel(repository)
        backgroundScope.launch { viewModel.providerConfigs.collect {} }
        advanceUntilIdle()

        assertEquals(ProviderId.entries.size, viewModel.providerConfigs.value.size)
    }

    @Test
    fun settingEnabledUpdatesTheRightProvider() = runTest {
        val repository = FakeProviderConfigRepository()
        val viewModel = AiProvidersViewModel(repository)
        backgroundScope.launch { viewModel.providerConfigs.collect {} }
        advanceUntilIdle()

        viewModel.setEnabled(ProviderId.ANTHROPIC, true)
        advanceUntilIdle()

        val configs = viewModel.providerConfigs.value
        assertTrue(configs.first { it.providerId == ProviderId.ANTHROPIC }.enabled)
        assertTrue(configs.filterNot { it.providerId == ProviderId.ANTHROPIC }.none { it.enabled })
    }
}
