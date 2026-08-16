package fr.vbrosseau.freshrssdiscover.domain.recap

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/**
 * Articles kept in the prompt.
 *
 * Five, the author's cap (GOAL-037-T08, tightened from ten on device): each
 * article gets its own clickable summary card, all five placeholders show
 * at once, and five is what a sheet holds without scrolling. It also keeps
 * the input far under the Prompt API's ~4000-token ceiling.
 */
const val RECAP_MAX_ARTICLES = 5

/**
 * Longest excerpt quoted per article, in characters.
 *
 * Aligned with what the list itself shows (`EXCERPT_MAX_LENGTH`): the recap
 * summarizes what the user could have read, and one prolific feed must not
 * consume the token budget of the nine others.
 */
const val RECAP_EXCERPT_MAX_CHARS = 240

/**
 * Builds the on-device prompt for the narrative brief.
 *
 * Numbered lines in, marked prose out: the `[N]` markers are what let the
 * paragraph be parsed back into article-bound segments ([parseRecapBrief])
 * so each underlined passage can open its original. Pure construction,
 * tested in plain JVM. The instructions are written in English — what small
 * instruction-tuned models follow most reliably — but the output language is
 * dictated by [language], the device language spelled out in English (e.g.
 * "French"): the brief must come out in whatever the user reads, with no
 * allow-list in the code.
 */
object RecapPrompt {
    /**
     * [articles] must not be empty: an empty feed has nothing to recap, and
     * the caller decides what that looks like on screen. [language] is a
     * display name, not a tag — "French" instructs the model better than
     * "fr-FR".
     */
    fun build(
        articles: List<Article>,
        language: String,
    ): String {
        require(articles.isNotEmpty()) { "Nothing to recap: the article list is empty." }

        val lines =
            articles.take(RECAP_MAX_ARTICLES).mapIndexed { position, article ->
                val excerpt =
                    article.summary.trim().let {
                        if (it.length > RECAP_EXCERPT_MAX_CHARS) it.take(RECAP_EXCERPT_MAX_CHARS) + "…" else it
                    }
                val body = if (excerpt.isEmpty()) "" else " — $excerpt"
                "${position + 1}. ${article.title} (${article.feed.title})$body"
            }

        return buildString {
            appendLine("You are given the articles of a personal news feed, one per line,")
            appendLine("numbered, as: \"N. title (source) — excerpt\".")
            appendLine("Write ONE short paragraph, three to six sentences, telling what")
            appendLine("happened across these articles — and, when several cover related")
            appendLine("stories, what connects them.")
            appendLine("In each statement, wrap the one or two words that best identify the")
            appendLine("article in curly braces, immediately followed by the article's number")
            appendLine("in square brackets — like: the {Tensor G6}[2] gains little.")
            appendLine("Never brace more than two words.")
            appendLine("Flowing prose only: no list, no introduction, no conclusion.")
            appendLine("Plain text only: no Markdown syntax, no asterisks.")
            appendLine("The paragraph is written in $language.")
            appendLine()
            appendLine("Articles:")
            lines.forEach(::appendLine)
        }
    }
}
