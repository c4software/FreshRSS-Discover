package fr.vbrosseau.freshrssdiscover.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Substitutes the main dispatcher during a test.
 *
 * `viewModelScope` runs on `Dispatchers.Main`, unavailable outside Android.
 * Without this rule, any ViewModel test would fail at the first `launch`.
 *
 * The dispatcher is unconfined: coroutines start immediately, so a ViewModel
 * `init` has produced its first state by the end of construction.
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
