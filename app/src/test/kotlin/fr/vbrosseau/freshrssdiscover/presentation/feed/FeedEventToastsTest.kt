package fr.vbrosseau.freshrssdiscover.presentation.feed

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The toast itself, past the engine event: what the user actually sees when
 * the API does not answer (GOAL-030).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class FeedEventToastsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Buffered like the ViewModel's channel: `tryEmit` must never drop. */
    private val events = MutableSharedFlow<FeedEvent>(extraBufferCapacity = 1)

    private fun show() {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                FeedEventToasts(events)
            }
        }
    }

    @Test
    fun aServerUnreachableEventShowsTheToast() {
        show()

        events.tryEmit(FeedEvent.ServerUnreachable)
        composeRule.waitForIdle()

        val expected = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.feed_server_unreachable_toast)
        assertEquals(expected, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun noEventShowsNoToast() {
        show()
        composeRule.waitForIdle()

        assertNull(ShadowToast.getLatestToast())
    }
}
