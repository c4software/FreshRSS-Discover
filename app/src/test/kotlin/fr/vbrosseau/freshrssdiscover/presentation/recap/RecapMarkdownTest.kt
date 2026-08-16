package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.text.font.FontWeight
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two Markdown tics observed on device, and nothing more: every extra
 * rule is a way to corrupt a digest that never contained Markdown.
 */
class RecapMarkdownTest {
    @Test
    fun aStarBulletBecomesATypographicOne() {
        assertEquals("• Un thème", digestAnnotated("* Un thème").text)
    }

    @Test
    fun aDashBulletBecomesATypographicOne() {
        assertEquals("• Un thème", digestAnnotated("- Un thème").text)
    }

    @Test
    fun boldMarkersDisappearIntoABoldSpan() {
        val annotated = digestAnnotated("* **Thème :** le texte")

        assertEquals("• Thème : le texte", annotated.text)
        val bold = annotated.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("Thème :", annotated.text.substring(bold.start, bold.end))
    }

    @Test
    fun everyLineIsConvertedIndependently() {
        val annotated = digestAnnotated("* Premier\n* Second")

        assertEquals("• Premier\n• Second", annotated.text)
    }

    @Test
    fun anUnterminatedBoldMarkerStaysVisible() {
        // Guessing its intent could swallow real asterisks.
        assertEquals("Un **reste", digestAnnotated("Un **reste").text)
        assertTrue(digestAnnotated("Un **reste").spanStyles.isEmpty())
    }

    @Test
    fun aBulletInTheMiddleOfALineIsLeftAlone() {
        assertEquals("2 * 3 = 6", digestAnnotated("2 * 3 = 6").text)
    }

    @Test
    fun plainTextPassesThroughUntouched() {
        val plain = "• Déjà propre, avec des mots en français."

        assertEquals(plain, digestAnnotated(plain).text)
    }
}
