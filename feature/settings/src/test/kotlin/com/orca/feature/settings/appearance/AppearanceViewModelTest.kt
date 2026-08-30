package com.orca.feature.settings.appearance

import com.orca.core.model.preferences.ThemeMode
import com.orca.core.testing.fake.FakeUserPreferencesRepository
import com.orca.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun settingThemeModeUpdatesUiState() = runTest {
        val repository = FakeUserPreferencesRepository()
        val viewModel = AppearanceViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun settingDynamicColorUpdatesUiState() = runTest {
        val repository = FakeUserPreferencesRepository()
        val viewModel = AppearanceViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setDynamicColorEnabled(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.dynamicColorEnabled)
    }
}
