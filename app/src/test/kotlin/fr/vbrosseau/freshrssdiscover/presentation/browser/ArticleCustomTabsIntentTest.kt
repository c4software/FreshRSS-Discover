package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Couleur arbitraire, seulement destinée à être retrouvée dans l'intention. */
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
     * L'absence de service lié est ce qui garantit qu'aucun `warmup` ni
     * `mayLaunchUrl` n'a pu partir : sans session, le navigateur n'a rien à
     * précharger, et aucune requête n'est émise avant le geste de
     * l'utilisateur (SPECS.md §7.4).
     */
    @Test
    fun noSessionIsBoundSoNothingIsPreconnected() {
        assertNull(intent.extras?.getBinder(CustomTabsIntent.EXTRA_SESSION))
    }
}
