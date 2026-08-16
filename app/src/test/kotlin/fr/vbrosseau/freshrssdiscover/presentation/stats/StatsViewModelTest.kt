package fr.vbrosseau.freshrssdiscover.presentation.stats

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.reminder.FakeReadingSessionRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class StatsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun theStoredHistogramIsServedToTheScreen() = runTest {
        val histogram =
            ReadingHistogram.Empty
                .record(day = 1, hour = 21)
                .record(day = 1, hour = 20)
                .record(day = 1, hour = 22)
                .record(day = 2, hour = 21)

        val viewModel = StatsViewModel(FakeReadingSessionRecorder(histogram))

        assertEquals(statsUiStateOf(histogram), viewModel.uiState.value)
    }

    @Test
    fun anEmptyHistoryIsServedAsAnEmptyState() = runTest {
        val viewModel = StatsViewModel(FakeReadingSessionRecorder())

        assertEquals(statsUiStateOf(ReadingHistogram.Empty), viewModel.uiState.value)
    }
}
