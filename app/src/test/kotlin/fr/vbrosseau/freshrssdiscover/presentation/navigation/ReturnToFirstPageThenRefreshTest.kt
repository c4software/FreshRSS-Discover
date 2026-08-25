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
 * The immersive reaction to a tab reselection (GOAL-039-T03), in isolation:
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
    fun aReselectionAwayFromTheFirstPageComesBackWithoutReloading() {
        // Two intentions, two taps: the first brings the reader back, and
        // must not also empty the feed they came back to.
        var refreshed = false
        show(initialPage = 3, onRefresh = { refreshed = true })

        composeRule.runOnIdle { assertNotNull(reselect)() }
        composeRule.waitForIdle()

        assertEquals(0, pagerState.settledPage)
        assertFalse(refreshed)
    }

    @Test
    fun aReselectionOnTheFirstPageReloads() {
        // Unlike the List: on the first item, the tap asks for something new,
        // the short-video convention (author's ruling, 2026-08-25).
        var refreshed = false
        show(initialPage = 0, onRefresh = { refreshed = true })

        composeRule.runOnIdle { assertNotNull(reselect)() }
        composeRule.waitForIdle()

        assertTrue(refreshed)
    }
}
