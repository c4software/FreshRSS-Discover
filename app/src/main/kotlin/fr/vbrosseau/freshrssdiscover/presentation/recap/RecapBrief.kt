package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Builds the brief's paragraph: linked segments come out underlined and
 * tinted [linkColor], tapping one calls [onSegmentClick] with its article
 * URL. A plain function rather than a Composable so the link wiring is
 * testable by invoking the annotations directly, without gestures.
 *
 * [tailBrush], when given, paints the last segment — the shimmer that says
 * "still being written" while the prose streams.
 */
internal fun briefAnnotated(
    segments: List<RecapSegmentUi>,
    linkColor: Color,
    onSegmentClick: (String) -> Unit,
    tailBrush: Brush? = null,
): AnnotatedString =
    buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            val prose = digestAnnotated(segment.text)
            val brush = tailBrush.takeIf { index == segments.lastIndex }
            if (segment.url != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = segment.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                        linkInteractionListener = { onSegmentClick(segment.url) },
                    ),
                ) {
                    appendBrushed(prose, brush)
                }
            } else {
                appendBrushed(prose, brush)
            }
        }
    }

private fun AnnotatedString.Builder.appendBrushed(
    prose: AnnotatedString,
    brush: Brush?,
) {
    if (brush == null) {
        append(prose)
    } else {
        withStyle(SpanStyle(brush = brush)) { append(prose) }
    }
}
