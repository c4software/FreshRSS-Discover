package fr.vbrosseau.freshrssdiscover.presentation.feed

/** Truncation mark, added only when text was removed. */
private const val ELLIPSIS = "…"

/**
 * Shortens a text without splitting a word.
 *
 * The cut lands on the last space before the limit: a sentence sliced
 * mid-word reads as a display defect, not an excerpt. A text without any
 * space (token, URL) is cut hard, for lack of better.
 *
 * Written once for both modes: only the bound changes between the List card
 * and the full-screen immersive mode (SPECS.md §8, questions 7 and 8); two
 * copies of the algorithm would diverge at the first fix.
 */
internal fun String.truncatedAtWord(maxLength: Int): String {
    if (length <= maxLength) return this

    val cut = take(maxLength)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > 0) cut.take(lastSpace) + ELLIPSIS else cut + ELLIPSIS
}
