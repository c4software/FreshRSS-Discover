package fr.vbrosseau.freshrssdiscover.presentation.browser

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le gabarit réel de `feed_article_share_text`, recopié : la décision testée
 * est la **composition** — le titre puis l'URL, et rien d'autre —, pas la
 * capacité d'Android à lire un fichier de ressources.
 */
private const val SHARE_TEXT_FORMAT = "%1\$s\n%2\$s"

/**
 * Aucune API Android ici, et c'est le but : ce qui se partage se décide en JVM
 * pure. Le contenu de l'intention est éprouvé à part, par
 * [ArticleShareIntentTest].
 */
class ArticleSharerTest {
    private val launcher = FakeArticleShareLauncher()
    private val sharer = ArticleSharer(launcher = launcher, textFormat = SHARE_TEXT_FORMAT)

    @Test
    fun theTitleThenTheLinkIsWhatLeaves() {
        val outcome = sharer.share(title = "Un titre", url = "https://example.org/article")

        assertEquals(ArticleShareOutcome.Shared, outcome)
        assertEquals(listOf("Un titre\nhttps://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun anHttpLinkIsSharedToo() {
        val outcome = sharer.share(title = "Un titre", url = "http://example.org/article")

        assertEquals(ArticleShareOutcome.Shared, outcome)
        assertEquals(listOf("Un titre\nhttp://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun aSchemeIsRecognizedWhateverItsCase() {
        val outcome = sharer.share(title = "Un titre", url = "HTTPS://example.org/article")

        assertEquals(ArticleShareOutcome.Shared, outcome)
    }

    @Test
    fun surroundingWhitespaceIsTrimmedBeforeSharing() {
        val outcome = sharer.share(title = "Un titre", url = "  https://example.org/article\n")

        assertEquals(ArticleShareOutcome.Shared, outcome)
        assertEquals(listOf("Un titre\nhttps://example.org/article"), launcher.sharedTexts)
    }

    @Test
    fun anAbsentLinkSharesNothing() {
        val outcome = sharer.share(title = "Un titre", url = null)

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun anEmptyLinkSharesNothing() {
        val outcome = sharer.share(title = "Un titre", url = "")

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aBlankLinkSharesNothing() {
        val outcome = sharer.share(title = "Un titre", url = "   ")

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    /**
     * Le lien vient d'un flux tiers non maîtrisé, et le sélecteur remettrait
     * ce texte tel quel à l'application choisie : refuser à l'ouverture sans
     * refuser au partage laisserait la porte entrouverte.
     */
    @Test
    fun aJavascriptLinkIsRefused() {
        val outcome = sharer.share(title = "Un titre", url = "javascript://void(0)")

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aFileLinkIsRefused() {
        val outcome = sharer.share(
            title = "Un titre",
            url = "file:///data/data/fr.vbrosseau.freshrssdiscover/databases/discover.db",
        )

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun anIntentLinkIsRefused() {
        val outcome = sharer.share(
            title = "Un titre",
            url = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
        )

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aLinkWithoutAuthorityIsRefused() {
        val outcome = sharer.share(title = "Un titre", url = "https://")

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }

    @Test
    fun aRelativeLinkIsRefused() {
        val outcome = sharer.share(title = "Un titre", url = "/2026/08/article.html")

        assertEquals(ArticleShareOutcome.Ignored, outcome)
        assertTrue(launcher.sharedTexts.isEmpty())
    }
}
