package fr.vbrosseau.freshrssdiscover.presentation.stats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val SufficientEvening: ReadingHistogram =
    ReadingHistogram.Empty
        .record(day = 1, hour = 21)
        .record(day = 1, hour = 20)
        .record(day = 1, hour = 22)
        .record(day = 2, hour = 21)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class StatsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(uiState: StatsUiState?) {
        composeRule.setContent { StatsScreen(uiState = uiState) }
    }

    @Test
    fun beforeTheDiskAnswersNothingIsShown() {
        show(uiState = null)

        composeRule.onRoot().onChildlessRoot()
    }

    @Test
    fun withoutAnySessionTheEmptyMessageReplacesTheChart() {
        show(statsUiStateOf(ReadingHistogram.Empty))

        composeRule.onNodeWithTag(StatsTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.CHART).assertDoesNotExist()
    }

    @Test
    fun theChartCarriesOneBarPerHour() {
        show(statsUiStateOf(SufficientEvening))

        composeRule.onNodeWithTag(StatsTestTags.CHART).assertIsDisplayed()
        composeRule.onAllNodesWithTag(StatsTestTags.barOf(21)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(StatsTestTags.barOf(0)).assertCountEquals(1)
    }

    /** The dominant hour is never carried by color alone (SPECS.md §7.1). */
    @Test
    fun theDominantHourIsSaidInWords() {
        show(statsUiStateOf(SufficientEvening))

        composeRule.onNodeWithTag(StatsTestTags.DOMINANT).assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.LEARNING).assertDoesNotExist()
    }

    @Test
    fun anInsufficientHistorySaysItIsStillLearning() {
        show(statsUiStateOf(ReadingHistogram.Empty.record(day = 1, hour = 21)))

        composeRule.onNodeWithTag(StatsTestTags.CHART).assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.LEARNING).assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.DOMINANT).assertDoesNotExist()
    }
}

/**
 * Asserts the root renders no content: `StatsScreen(null)` composes nothing,
 * and the only observable trace is a root without children.
 */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.onChildlessRoot() {
    val children = fetchSemanticsNode().children
    check(children.isEmpty()) { "l'écran devrait être vide, trouvé ${children.size} enfants" }
}
