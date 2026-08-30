package com.gecko.core.testing.rule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher so ViewModels using `viewModelScope` are
 * testable. Uses [UnconfinedTestDispatcher] (not [kotlinx.coroutines.test.StandardTestDispatcher])
 * deliberately: a JUnit rule has no access to the enclosing `runTest`'s scheduler, so a
 * Standard dispatcher here would run on its own disconnected scheduler that `advanceUntilIdle()`
 * never touches. Unconfined executes eagerly instead, sidestepping that entirely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
