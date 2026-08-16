package fr.vbrosseau.freshrssdiscover.domain.recap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The brief parser, tested on what a streaming small model actually
 * produces: braced key words, bare markers, a truncated tail, and prose
 * that ignores the format altogether. What matters throughout: only a
 * couple of words end up bound, never a whole sentence.
 */
class RecapSegmentTest {
    @Test
    fun bracedKeyWordsAloneAreBoundToTheirArticle() {
        val segments = parseRecapBrief("La bêta de {GNOME 51}[1] retouche tout.")

        assertEquals(
            listOf(
                RecapSegment(text = "La bêta de ", articleIndex = null),
                RecapSegment(text = "GNOME 51", articleIndex = 1),
                RecapSegment(text = " retouche tout.", articleIndex = null),
            ),
            segments,
        )
    }

    @Test
    fun aBareMarkerBindsOnlyTheCoupleOfWordsBeforeIt() {
        // The drift seen on device: the model marks whole statements, and
        // binding the full run underlined entire sentences.
        val segments = parseRecapBrief("Toute une phrase sur le même procès [2].")

        assertEquals(
            listOf(
                RecapSegment(text = "Toute une phrase sur le ", articleIndex = null),
                RecapSegment(text = "même procès", articleIndex = 2),
                RecapSegment(text = ".", articleIndex = null),
            ),
            segments,
        )
    }

    @Test
    fun aHalfStreamedBraceOrMarkerStaysHidden() {
        assertEquals(
            listOf(RecapSegment(text = "Le début", articleIndex = null)),
            parseRecapBrief("Le début {Tensor G"),
        )
        assertEquals(
            listOf(RecapSegment(text = "Le début", articleIndex = null)),
            parseRecapBrief("Le début [1"),
        )
        assertEquals(
            listOf(RecapSegment(text = "Le début", articleIndex = null)),
            parseRecapBrief("Le début {Tensor G6}[1"),
        )
    }

    @Test
    fun strayBracesWithoutAMarkerDisappearFromTheProse() {
        val segments = parseRecapBrief("Des {accolades} sans numéro restent lisibles.")

        assertEquals("Des accolades sans numéro restent lisibles.", segments.single().text)
    }

    @Test
    fun anEnumerationOfMarkersUnderlinesNoPunctuation() {
        // Seen on device: "les articles [2], [3] et [4]" came out as the
        // glued "les articles,,et", commas underlined.
        val segments = parseRecapBrief("les articles [2], [3] et [4] détaillent")

        assertEquals(listOf(2), segments.mapNotNull { it.articleIndex })
        assertEquals("les articles,  et  détaillent", segments.joinToString("") { it.text })
    }

    @Test
    fun proseWithoutMarkersBecomesOneUnlinkedSegment() {
        val segments = parseRecapBrief("Un paragraphe sans le format demandé.")

        assertEquals(listOf(RecapSegment("Un paragraphe sans le format demandé.", null)), segments)
    }

    @Test
    fun aBlankAnswerParsesToNothing() {
        assertTrue(parseRecapBrief("  \n").isEmpty())
    }

    @Test
    fun anAbsurdNumberKeepsItsProseUnlinked() {
        val segments = parseRecapBrief("Débordement massif [99999999999999999999].")

        assertEquals(RecapSegment(text = "Débordement massif ", articleIndex = null), segments.first())
    }
}
