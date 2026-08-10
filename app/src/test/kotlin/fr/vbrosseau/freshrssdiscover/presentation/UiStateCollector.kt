package fr.vbrosseau.freshrssdiscover.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Keeps a subscriber on [flow] for the whole duration of the test.
 *
 * ViewModels publish their state with `WhileSubscribed`: without a screen,
 * hence without a subscriber, sources are not observed and `uiState.value`
 * would stay frozen on the initial state. The collector lives in
 * `backgroundScope` and dies with the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepCollecting(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
}
