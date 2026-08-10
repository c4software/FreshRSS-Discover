package fr.vbrosseau.freshrssdiscover.presentation.browser

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real `feed_article_share_text` template, copied: the decision under test
 * is the composition (title then URL, nothing else), not Android's ability to
 * read a resource file.
 */
private const val SHARE_TEXT_FORMAT = "%1\$s\n%2\$s"

/**
 * No Android API here, by design: what gets shared is decided in pure JVM and
 * observed on the launcher (what left, or nothing). The intent content is
 * tested separately by [ArticleShareIntentTest].
 */
class ArticleSharerTest {
    private val launcher = FakeArticleShareLauncher()
    private val sharer = ArticleSharer(launcher = launcher, textFormat = SHARE_TEXT_FORMAT)

    @Test
    fun theTitleThenTheLinkIsWhatLeaves() {
        sharer.share(title = "Un titre", url = "https://example.org/article")

        assertEquals(listOf("Un titre\nhttps://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun anHttpLinkIsSharedToo() {
        sharer.share(title = "Un titre", url = "http://example.org/article")

        assertEquals(listOf("Un titre\nhttp://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun aSchemeIsRecognizedWhateverItsCase() {
        sharer.share(title = "Un titre", url = "HTTPS://example.org/article")

        assertEquals(listOf("Un titre\nHTTPS://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun surroundingWhitespaceIsTrimmedBeforeSharing() {
        sharer.share(title = "Un titre", url = "  https://example.org/article\n")

        assertEquals(listOf("Un titre\nhttps://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun anAbsentLinkSharesNothing() {
        sharer.share(title = "Un titre", url = null)

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun anEmptyLinkSharesNothing() {
        sharer.share(title = "Un titre", url = "")

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aBlankLinkSharesNothing() {
        sharer.share(title = "Un titre", url = "   ")

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    /**
     * The link comes from an untrusted third-party feed, and the chooser would
     * hand this text as-is to the chosen app: refusing on open without
     * refusing on share would leave the door ajar.
     */
    @Test
    fun aJavascriptLinkIsRefused() {
        sharer.share(title = "Un titre", url = "javascript://void(0)")

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aFileLinkIsRefused() {
        sharer.share(
            title = "Un titre",
            url = "file:///data/data/fr.vbrosseau.freshrssdiscover/databases/discover.db",
        )

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun anIntentLinkIsRefused() {
        sharer.share(
            title = "Un titre",
            url = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
        )

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aLinkWithoutAuthorityIsRefused() {
        sharer.share(title = "Un titre", url = "https://")

        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aRelativeLinkIsRefused() {
        sharer.share(title = "Un titre", url = "/2026/08/article.html")

        assertTrue(launcher.sharedTexts.isEmpty())
    }
}
