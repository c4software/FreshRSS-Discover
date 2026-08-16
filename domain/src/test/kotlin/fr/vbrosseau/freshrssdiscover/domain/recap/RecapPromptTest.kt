package fr.vbrosseau.freshrssdiscover.domain.recap

import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.feed.feedRef
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The prompt, tested where the token budget can silently blow up: the feed
 * with more articles than the cap, the outsized excerpt, and the empty list
 * that has nothing to say.
 */
class RecapPromptTest {
    @Test
    fun thePromptDemandsTheGivenLanguage() {
        val prompt = RecapPrompt.build(listOf(article()), language = "French")

        assertContains(prompt, "written in French")
    }

    @Test
    fun eachArticleCarriesTitleSourceAndExcerpt() {
        val prompt =
            RecapPrompt.build(
                listOf(
                    article(
                        title = "Le titre",
                        summary = "L'extrait.",
                        feed = feedRef(title = "Le flux"),
                    ),
                ),
                language = "French",
            )

        assertContains(prompt, "1. Le titre (Le flux) — L'extrait.")
    }

    @Test
    fun aBlankExcerptLeavesTheTitleAlone() {
        val prompt =
            RecapPrompt.build(
                listOf(article(title = "Sans extrait", summary = "  ")),
                language = "French",
            )

        assertContains(prompt, "1. Sans extrait (Un flux)\n")
        assertFalse(prompt.contains("Sans extrait (Un flux) —"))
    }

    @Test
    fun articlesBeyondTheCapAreLeftOut() {
        val articles = (1..RECAP_MAX_ARTICLES + 1).map { article(id = it.toLong(), title = "Titre $it") }

        val prompt = RecapPrompt.build(articles, language = "French")

        assertContains(prompt, "Titre $RECAP_MAX_ARTICLES")
        assertFalse(prompt.contains("Titre ${RECAP_MAX_ARTICLES + 1}"))
    }

    @Test
    fun anOutsizedExcerptIsTruncatedWithAnEllipsis() {
        val prompt =
            RecapPrompt.build(
                listOf(article(summary = "a".repeat(RECAP_EXCERPT_MAX_CHARS + 50))),
                language = "French",
            )

        assertContains(prompt, "a".repeat(RECAP_EXCERPT_MAX_CHARS) + "…")
        assertFalse(prompt.contains("a".repeat(RECAP_EXCERPT_MAX_CHARS + 1)))
    }

    @Test
    fun anExcerptAtTheCapIsQuotedWhole() {
        val exact = "b".repeat(RECAP_EXCERPT_MAX_CHARS)

        val prompt = RecapPrompt.build(listOf(article(summary = exact)), language = "French")

        assertContains(prompt, "— $exact\n")
        assertFalse(prompt.contains("$exact…"))
    }

    @Test
    fun anEmptyListIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            RecapPrompt.build(emptyList(), language = "French")
        }
    }

    @Test
    fun theInstructionsForbidMarkdown() {
        val prompt = RecapPrompt.build(listOf(article()), language = "French")

        assertContains(prompt, "no Markdown syntax")
    }

    @Test
    fun theInstructionsComeBeforeTheArticles() {
        val prompt = RecapPrompt.build(listOf(article(title = "Un titre")), language = "French")

        assertTrue(prompt.indexOf("Articles:") < prompt.indexOf("1. Un titre"))
    }

    @Test
    fun exactlyOneLinePerArticle() {
        val articles = listOf(article(id = 1, title = "Premier"), article(id = 2, title = "Second"))

        val prompt = RecapPrompt.build(articles, language = "French")

        assertEquals(
            listOf("1. Premier (Un flux) — Un extrait.", "2. Second (Un flux) — Un extrait."),
            prompt.lines().filter { parseRecapLines(it).isNotEmpty() },
        )
    }
}
