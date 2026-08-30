package com.gecko.feature.settings.chatprefs

import com.gecko.core.testing.fake.FakeUserPreferencesRepository
import com.gecko.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatPreferencesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun togglingSendOnEnterUpdatesUiState() = runTest {
        val repository = FakeUserPreferencesRepository()
        val viewModel = ChatPreferencesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSendOnEnter(false)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.sendOnEnter)
    }

    @Test
    fun togglingStreamingUpdatesUiState() = runTest {
        val repository = FakeUserPreferencesRepository()
        val viewModel = ChatPreferencesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setStreamingEnabled(false)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.streamingEnabled)
    }
}
