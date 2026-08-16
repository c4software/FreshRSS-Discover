package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** `* item` or `- item`, the two bullet spellings small models fall back to. */
private val MarkdownBullet = Regex("""^\s*[*-]\s+""")

/**
 * Renders the model's Markdown tics instead of showing them raw.
 *
 * The prompt demands plain text, but a small model disobeys often enough
 * that raw `**` reached the first on-device digest. Only the two shapes
 * actually observed are handled — leading `*`/`-` bullets become `•`, and
 * `**bold**` becomes bold — because each unrequested rule here is a way to
 * corrupt a digest that never contained Markdown at all. An unterminated
 * `**` stays visible: guessing its intent could swallow real asterisks.
 */
internal fun digestAnnotated(text: String): AnnotatedString =
    buildAnnotatedString {
        text.lines().forEachIndexed { index, line ->
            if (index > 0) append('\n')
            appendWithBold(line.replace(MarkdownBullet, "• "))
        }
    }

private fun AnnotatedString.Builder.appendWithBold(line: String) {
    var rest = line
    while (true) {
        val start = rest.indexOf("**")
        val end = if (start == -1) -1 else rest.indexOf("**", start + 2)
        if (end == -1) {
            append(rest)
            return
        }
        append(rest.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(rest.substring(start + 2, end))
        }
        rest = rest.substring(end + 2)
    }
}
