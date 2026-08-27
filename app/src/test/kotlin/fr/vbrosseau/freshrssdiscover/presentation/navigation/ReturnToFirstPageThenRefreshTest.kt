package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Enough pages for a reselection away from the first one to have somewhere to go. */
private const val PAGE_COUNT = 5

/**
 * The immersive reaction to a tab reselection (GOAL-042-T02), in isolation:
 * the route needs injected ViewModels, while the rule lives entirely in
 * [rememberReturnToFirstPageThenRefresh].
 */
@RunWith(RobolectricTestRunner::class)
class ReturnToFirstPageThenRefreshTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var reselect: (() -> Unit)? = null
    private lateinit var pagerState: PagerState

    private fun show(initialPage: Int, onRefresh: () -> Unit) {
        composeRule.setContent {
            pagerState = rememberPagerState(initialPage = initialPage) { PAGE_COUNT }
            reselect = rememberReturnToFirstPageThenRefresh(pagerState = pagerState, onRefresh = onRefresh)
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aReselectionAwayFromTheFirstPageComesBackThenReloads() {
        // The return is shown, not skipped: the reload only fires once the
        // pager has settled on the first page (SPECS.md §4.6).
        var refreshed = false
        show(initialPage = 3, onRefresh = { refreshed = true })

        composeRule.runOnIdle { assertNotNull(reselect)() }
        composeRule.waitForIdle()

        assertEquals(0, pagerState.settledPage)
        assertTrue(refreshed)
    }

    @Test
    fun aReselectionOnTheFirstPageDoesNothing() {
        // Like the List's top tap: nowhere to bring the reader back to, and
        // a reload would empty a feed the tap never asked to lose.
        var refreshed = false
        show(initialPage = 0, onRefresh = { refreshed = true })

        composeRule.runOnIdle { assertNotNull(reselect)() }
        composeRule.waitForIdle()

        assertFalse(refreshed)
    }
}
