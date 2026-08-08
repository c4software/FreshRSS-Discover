package fr.vbrosseau.freshrssdiscover.presentation.feed

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La règle qui décide du fond flouté (SPECS.md §4.3).
 *
 * Ce qui compte ici n'est pas l'inégalité — elle ne surprend personne — mais
 * les trois cas où l'on ne doit **rien** faire : image assez large, image
 * exactement à la taille, et taille inconnue. Le dernier est le plus important :
 * flouter sur une supposition abîmerait des images correctes.
 */
class IllustrationFitTest {
    @Test
    fun anImageNarrowerThanItsSlotWouldBeUpscaled() {
        assertTrue(needsUpscaling(sourceWidthPx = 200, slotWidthPx = 1080))
    }

    @Test
    fun anImageWiderThanItsSlotIsLeftAlone() {
        // Elle sera rognée, jamais étirée : c'est le cas ordinaire d'un bandeau
        // d'article, et il n'a rien à corriger.
        assertFalse(needsUpscaling(sourceWidthPx = 1920, slotWidthPx = 1080))
    }

    @Test
    fun anImageExactlyAtItsSlotWidthIsLeftAlone() {
        // Aucun agrandissement : la borne est stricte, sans quoi le procédé se
        // déclencherait sur une image parfaitement nette.
        assertFalse(needsUpscaling(sourceWidthPx = 1080, slotWidthPx = 1080))
    }

    @Test
    fun anUnknownSourceSizeDecidesNothing() {
        // L'image est encore en vol, ou Coil n'a pas rendu sa taille : on ne
        // floute pas sur une supposition.
        assertFalse(needsUpscaling(sourceWidthPx = 0, slotWidthPx = 1080))
        assertFalse(needsUpscaling(sourceWidthPx = -1, slotWidthPx = 1080))
    }

    @Test
    fun anUnmeasuredSlotDecidesNothing() {
        // Première composition : la largeur n'est pas encore connue. Décider à
        // ce moment-là ferait clignoter le fond dès la mesure suivante.
        assertFalse(needsUpscaling(sourceWidthPx = 200, slotWidthPx = 0))
    }

    @Test
    fun aTallButWideEnoughImageIsLeftAlone() {
        // La hauteur ne participe pas : une image large et haute est assez
        // définie, et la traiter ferait flouter des bannières nettes.
        assertFalse(needsUpscaling(sourceWidthPx = 1200, slotWidthPx = 1080))
    }
}
