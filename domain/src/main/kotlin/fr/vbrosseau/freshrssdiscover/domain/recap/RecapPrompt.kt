package fr.vbrosseau.freshrssdiscover.domain.recap

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/**
 * Articles kept in the prompt.
 *
 * The Prompt API truncates or refuses beyond ~4000 input tokens. Twenty
 * articles of a title plus a bounded excerpt stay well under that ceiling
 * while covering everything a normal reading session leaves unread; beyond
 * twenty, a digest stops being a digest.
 */
const val RECAP_MAX_ARTICLES = 20

/**
 * Longest excerpt quoted per article, in characters.
 *
 * Aligned with what the list itself shows (`EXCERPT_MAX_LENGTH`): the recap
 * summarizes what the user could have read, and one prolific feed must not
 * consume the token budget of the nineteen others.
 */
const val RECAP_EXCERPT_MAX_CHARS = 240

/**
 * Builds the on-device prompt for the recap of unread articles.
 *
 * Pure construction, tested in plain JVM. The instructions are written in
 * English — what small instruction-tuned models follow most reliably — but
 * the output language is dictated by [language], the device language spelled
 * out in English (e.g. "French"): the recap must come out in whatever the
 * user reads, with no allow-list in the code.
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
            articles.take(RECAP_MAX_ARTICLES).map { article ->
                val excerpt =
                    article.summary.trim().let {
                        if (it.length > RECAP_EXCERPT_MAX_CHARS) it.take(RECAP_EXCERPT_MAX_CHARS) + "…" else it
                    }
                val body = if (excerpt.isEmpty()) "" else " — $excerpt"
                "- ${article.title} (${article.feed.title})$body"
            }

        return buildString {
            appendLine("You are given the unread articles of a personal news feed, one per line,")
            appendLine("as: title (source) — excerpt.")
            appendLine("Write a short digest of what happened, grouped by theme, as concise")
            appendLine("bullet points. Do not list the articles one by one, do not add an")
            appendLine("introduction or a conclusion.")
            // Small models sprinkle Markdown by default, and the sheet shows
            // text: asked for here AND neutralized at display, belt and braces.
            appendLine("Plain text only: no Markdown syntax, no asterisks; start each bullet")
            appendLine("with \"• \".")
            appendLine("Answer only with the digest, written in $language.")
            appendLine()
            appendLine("Articles:")
            lines.forEach(::appendLine)
        }
    }
}
