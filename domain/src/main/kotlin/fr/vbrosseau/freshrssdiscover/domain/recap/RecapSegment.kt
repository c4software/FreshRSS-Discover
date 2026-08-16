package fr.vbrosseau.freshrssdiscover.domain.recap

/**
 * One run of the brief's prose: its text, and the article it draws from —
 * `null` for connective tissue that belongs to no single article.
 */
data class RecapSegment(
    val text: String,
    val articleIndex: Int?,
)

/*
 * `\}` and `\]` are escaped even where the desktop JVM would not require
 * it: Android's ICU regex engine rejects them bare, and the JVM tests
 * cannot see that — the first device run crashed on class load.
 */

/**
 * `{key words}[N]`, or a bare `[N]`: the braces are the demanded form, the
 * bare marker the drift small models produce anyway. A bracket opening on a
 * digit is absorbed whole and bound to its **first** number — the model
 * writes `[2, 4]` despite the prompt, and half a raw bracket reached the
 * screen. The bare form swallows no surrounding space: a marker enumeration
 * ("[2], [3] et [4]") must leave its punctuation exactly as written.
 */
private val LinkedRun = Regex("""\{([^{}]+)\}\s*\[(\d+)[^\]]*\]|\[(\d+)[^\]]*\]""")

/** A `{key wo`, `{key}[12` or `[2, ` still streaming: hidden until it closes. */
private val TrailingPartial = Regex("""\s*\{[^{}]*(\}\s*\[[^\]]*)?$|\s*\[[^\]]*$""")

/** The last couple of words: what a bare marker binds instead of its run. */
private val TrailingWords = Regex("""\S+(?:\s+\S+)?\s*$""")

private const val BRACED_WORDS_GROUP = 1
private const val BRACED_INDEX_GROUP = 2
private const val BARE_INDEX_GROUP = 3

/**
 * Below this, a bare marker binds nothing: what precedes an enumerated
 * marker is a comma or an "et", and underlining those glued the prose into
 * "les articles,,et" on device (GOAL-037-T17).
 */
private const val MIN_LINKED_LETTERS = 3

/**
 * Splits the model's brief into segments, binding to each article only the
 * **few words** its marker designates — braced key words when the model
 * followed the format, the last two words before a bare marker otherwise:
 * binding the whole run underlined entire sentences (seen on device,
 * GOAL-037-T16).
 *
 * Built to run on a **partial** text: generation streams, and a half-written
 * brace or marker is hidden rather than shown raw. A brief with no marker at
 * all comes back as one unlinked segment — a model that drops the format
 * degrades to a plain readable paragraph, never to a blank sheet.
 */
fun parseRecapBrief(output: String): List<RecapSegment> {
    val visible = output.replace(TrailingPartial, "")
    if (visible.isBlank()) return emptyList()

    val segments = mutableListOf<RecapSegment>()

    fun addPlain(text: String) {
        val cleaned = text.replace("{", "").replace("}", "")
        if (cleaned.isNotBlank()) segments += RecapSegment(text = cleaned, articleIndex = null)
    }

    var consumed = 0
    LinkedRun.findAll(visible).forEach { match ->
        val before = visible.substring(consumed, match.range.first)
        val bracedWords = match.groupValues[BRACED_WORDS_GROUP]
        if (bracedWords.isNotEmpty()) {
            addPlain(before)
            segments +=
                RecapSegment(
                    text = bracedWords,
                    articleIndex = match.groupValues[BRACED_INDEX_GROUP].toIntOrNull(),
                )
        } else {
            val index = match.groupValues[BARE_INDEX_GROUP].toIntOrNull()
            val keyWords =
                index?.let { TrailingWords.find(before) }
                    ?.takeIf { it.value.count(Char::isLetterOrDigit) >= MIN_LINKED_LETTERS }
            if (keyWords == null) {
                addPlain(before)
            } else {
                addPlain(before.substring(0, keyWords.range.first))
                segments += RecapSegment(text = keyWords.value.trimEnd(), articleIndex = index)
            }
        }
        consumed = match.range.last + 1
    }
    addPlain(visible.substring(consumed))

    return segments
}
