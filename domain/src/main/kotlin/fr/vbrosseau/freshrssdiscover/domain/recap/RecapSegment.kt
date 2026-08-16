package fr.vbrosseau.freshrssdiscover.domain.recap

/**
 * One run of the brief's prose: its text, and the article it draws from —
 * `null` for connective tissue that belongs to no single article.
 */
data class RecapSegment(
    val text: String,
    val articleIndex: Int?,
)

/** `[N]`, swallowing the space before it so no gap is left behind. */
private val Marker = Regex("""\s*\[(\d+)]""")

/** A `[12` still being streamed: hidden until its closing bracket arrives. */
private val TrailingPartialMarker = Regex("""\s*\[\d*$""")

/**
 * Splits the model's brief into segments, each bound to the article whose
 * marker closes it.
 *
 * Built to run on a **partial** text: generation streams, and the sheet
 * renders the prose as it grows — a half-written marker is hidden rather
 * than shown raw. A brief with no marker at all comes back as one unlinked
 * segment: a model that drops the format degrades to a plain readable
 * paragraph, never to a blank sheet.
 */
fun parseRecapBrief(output: String): List<RecapSegment> {
    val visible = output.replace(TrailingPartialMarker, "")
    if (visible.isBlank()) return emptyList()

    val segments = mutableListOf<RecapSegment>()
    var consumed = 0
    Marker.findAll(visible).forEach { marker ->
        val text = visible.substring(consumed, marker.range.first)
        if (text.isNotBlank()) {
            segments += RecapSegment(text = text, articleIndex = marker.groupValues[1].toIntOrNull())
        }
        consumed = marker.range.last + 1
    }
    val remainder = visible.substring(consumed)
    if (remainder.isNotBlank()) segments += RecapSegment(text = remainder, articleIndex = null)

    return segments
}
