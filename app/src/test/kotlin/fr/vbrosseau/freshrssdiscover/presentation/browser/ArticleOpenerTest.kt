package fr.vbrosseau.freshrssdiscover.presentation.browser

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robolectric, uniquement pour que `ActivityNotFoundException` soit une vraie
 * classe et non le talon de `android.jar` qui lève « Stub! » à la construction.
 * La décision testée, elle, ne dépend d'aucune API Android.
 *
 * La décision s'observe sur le **lanceur** — ce qui a été lancé, ou rien :
 * `open` ne rend aucune issue, aucun appelant n'en ayant l'usage.
 */
@RunWith(RobolectricTestRunner::class)
class ArticleOpenerTest {
    private val launcher = FakeCustomTabLauncher()
    private val opener = ArticleOpener(launcher)

    @Test
    fun anHttpsLinkIsOpened() {
        opener.open("https://example.org/article")

        assertEquals(listOf("https://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun anHttpLinkIsOpened() {
        opener.open("http://example.org/article")

        assertEquals(listOf("http://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun aSchemeIsRecognizedWhateverItsCase() {
        opener.open("HTTPS://example.org/article")

        assertEquals(listOf("HTTPS://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun surroundingWhitespaceIsTrimmedBeforeOpening() {
        opener.open("  https://example.org/article\n")

        assertEquals(listOf("https://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun anAbsentLinkOpensNothing() {
        opener.open(null)

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun anEmptyLinkOpensNothing() {
        opener.open("")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aBlankLinkOpensNothing() {
        opener.open("   ")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aJavascriptLinkIsRefused() {
        opener.open("javascript://void(0)")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aFileLinkIsRefused() {
        opener.open("file:///data/data/fr.vbrosseau.freshrssdiscover/databases/discover.db")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun anIntentLinkIsRefused() {
        opener.open("intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aLinkWithoutAuthorityIsRefused() {
        opener.open("https://")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aRelativeLinkIsRefused() {
        opener.open("/2026/08/article.html")

        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun anAbsentBrowserDoesNotCrash() {
        // Image système minimale, ou navigateur désactivé par une politique
        // d'entreprise : `startActivity` lève, et le geste doit rester sans
        // conséquence.
        val strippedDevice = ArticleOpener(FakeCustomTabLauncher(browserInstalled = false))

        strippedDevice.open("https://example.org/article")
    }
}
