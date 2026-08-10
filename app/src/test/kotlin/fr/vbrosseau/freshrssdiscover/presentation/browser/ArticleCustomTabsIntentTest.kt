package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Arbitrary color, only meant to be found back in the intent. */
private const val THEME_SURFACE_COLOR = 0xFF102030.toInt()

@RunWith(RobolectricTestRunner::class)
class ArticleCustomTabsIntentTest {
    private val intent = buildArticleCustomTabsIntent(THEME_SURFACE_COLOR).intent

    @Test
    fun theCustomTabOpensTheLinkAsAView() {
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun theToolbarUsesTheColorGivenByTheTheme() {
        assertEquals(THEME_SURFACE_COLOR, intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0))
    }

    @Test
    fun thePageTitleIsShownNextToTheDomain() {
        assertEquals(
            CustomTabsIntent.SHOW_PAGE_TITLE,
            intent.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE),
        )
    }

    /**
     * The absence of a bound service is what guarantees no `warmup` or
     * `mayLaunchUrl` could have gone out: without a session, the browser has
     * nothing to preload, and no request is emitted before the user's gesture
     * (SPECS.md §7.4).
     */
    @Test
    fun noSessionIsBoundSoNothingIsPreconnected() {
        assertNull(intent.extras?.getBinder(CustomTabsIntent.EXTRA_SESSION))
    }
}
