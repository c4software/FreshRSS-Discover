package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The paragraph builder: linked passages carry their underline and their
 * tap, connective prose carries neither. Links are exercised through their
 * annotations directly — that is why the builder is a plain function.
 */
class RecapBriefTest {
    private val segments = listOf(
        RecapSegmentUi(text = "GNOME ouvre sa bêta", url = "https://exemple.org/gnome"),
        RecapSegmentUi(text = ", pendant que le procès continue.", url = null),
    )

    @Test
    fun theProseReadsAsOneParagraph() {
        val brief = briefAnnotated(segments, Color.Blue, onSegmentClick = {})

        assertEquals("GNOME ouvre sa bêta, pendant que le procès continue.", brief.text)
    }

    @Test
    fun onlyTheSourcedPassageIsUnderlinedAndTinted() {
        val brief = briefAnnotated(segments, Color.Blue, onSegmentClick = {})

        val link = brief.getLinkAnnotations(0, brief.text.length).single()
        assertEquals("GNOME ouvre sa bêta", brief.text.substring(link.start, link.end))
        val style = (link.item as LinkAnnotation.Clickable).styles?.style
        assertEquals(TextDecoration.Underline, style?.textDecoration)
        assertEquals(Color.Blue, style?.color)
    }

    @Test
    fun tappingAPassageOpensItsArticle() {
        var opened: String? = null
        val brief = briefAnnotated(segments, Color.Blue, onSegmentClick = { opened = it })

        val link = brief.getLinkAnnotations(0, brief.text.length).single().item
        (link as LinkAnnotation.Clickable).linkInteractionListener?.onClick(link)

        assertEquals("https://exemple.org/gnome", opened)
    }

    @Test
    fun theShimmerBrushOnlyPaintsTheTail() {
        val brief = briefAnnotated(
            segments,
            Color.Blue,
            onSegmentClick = {},
            // A gradient, not SolidColor: SpanStyle exposes a solid brush as
            // `color`, and the assertion below reads `brush`.
            tailBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Red, Color.Blue)),
        )

        val brushed = brief.spanStyles.filter { it.item.brush != null }
        assertEquals(1, brushed.size)
        assertTrue(brushed.single().start >= brief.text.indexOf(", pendant"))
    }

    @Test
    fun modelMarkdownInsideAPassageIsStillRendered() {
        val brief = briefAnnotated(
            listOf(RecapSegmentUi(text = "un **gras** têtu", url = null)),
            Color.Blue,
            onSegmentClick = {},
        )

        assertEquals("un gras têtu", brief.text)
    }
}
