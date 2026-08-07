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
 */
@RunWith(RobolectricTestRunner::class)
class ArticleOpenerTest {
    private val launcher = FakeCustomTabLauncher()
    private val opener = ArticleOpener(launcher)

    @Test
    fun anHttpsLinkIsOpened() {
        val outcome = opener.open("https://example.org/article")

        assertEquals(ArticleOpenOutcome.Opened, outcome)
        assertEquals(listOf("https://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun anHttpLinkIsOpened() {
        val outcome = opener.open("http://example.org/article")

        assertEquals(ArticleOpenOutcome.Opened, outcome)
        assertEquals(listOf("http://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun aSchemeIsRecognizedWhateverItsCase() {
        val outcome = opener.open("HTTPS://example.org/article")

        assertEquals(ArticleOpenOutcome.Opened, outcome)
    }

    @Test
    fun surroundingWhitespaceIsTrimmedBeforeOpening() {
        val outcome = opener.open("  https://example.org/article\n")

        assertEquals(ArticleOpenOutcome.Opened, outcome)
        assertEquals(listOf("https://example.org/article"), launcher.launchedUrls)
    }

    @Test
    fun anAbsentLinkOpensNothing() {
        val outcome = opener.open(null)

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun anEmptyLinkOpensNothing() {
        val outcome = opener.open("")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aBlankLinkOpensNothing() {
        val outcome = opener.open("   ")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aJavascriptLinkIsRefused() {
        val outcome = opener.open("javascript://void(0)")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aFileLinkIsRefused() {
        val outcome = opener.open("file:///data/data/fr.vbrosseau.freshrssdiscover/databases/discover.db")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun anIntentLinkIsRefused() {
        val outcome = opener.open("intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aLinkWithoutAuthorityIsRefused() {
        val outcome = opener.open("https://")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun aRelativeLinkIsRefused() {
        val outcome = opener.open("/2026/08/article.html")

        assertEquals(ArticleOpenOutcome.Ignored, outcome)
        assertTrue(launcher.launchedUrls.isEmpty())
    }

    @Test
    fun anAbsentBrowserIsReportedWithoutCrashing() {
        val strippedDevice = ArticleOpener(FakeCustomTabLauncher(browserInstalled = false))

        val outcome = strippedDevice.open("https://example.org/article")

        assertEquals(ArticleOpenOutcome.NoBrowser, outcome)
    }
}
