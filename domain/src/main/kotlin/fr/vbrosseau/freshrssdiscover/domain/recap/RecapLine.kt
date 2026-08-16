package fr.vbrosseau.freshrssdiscover.domain.recap

/**
 * One parsed line of the model's answer: the article number it claims to
 * summarize, and the summary text.
 */
data class RecapLine(
    val index: Int,
    val text: String,
)

/** `N. summary` as demanded, tolerating `N)` since small models drift. */
private val NumberedLine = Regex("""^\s*(\d+)[.)]\s*(.+)$""")

/**
 * Parses the model's streamed answer back into numbered summaries.
 *
 * Built to run on a **partial** text: generation streams, and the sheet
 * shows summaries as their lines complete. A line that does not match the
 * demanded shape is dropped — the caller falls back to showing the raw text
 * whole when nothing at all matches, so a disobedient model degrades to an
 * unlinked digest rather than to a blank sheet.
 */
fun parseRecapLines(output: String): List<RecapLine> =
    output.lines().mapNotNull { line ->
        NumberedLine.matchEntire(line)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { index ->
                RecapLine(index = index, text = match.groupValues[2].trim())
            }
        }
    }
