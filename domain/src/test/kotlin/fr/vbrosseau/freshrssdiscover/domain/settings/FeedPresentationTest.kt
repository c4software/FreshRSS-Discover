package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedPresentationTest {
    @Test
    fun theListIsTheModeAFreshInstallationOpensIn() {
        // SPECS.md §4.8 : c'est le mode par défaut, et il l'est parce qu'il
        // montre de quoi le flux est fait avant d'exiger un geste.
        assertEquals(FeedPresentation.List, FeedPresentation.Default)
    }

    @Test
    fun eachModeIsStoredUnderItsOwnName() {
        // Ces chaînes sont écrites sur disque : les renommer casserait la
        // relecture des préférences existantes, et ce test le rend visible.
        assertEquals("List", FeedPresentation.List.name)
        assertEquals("Swipe", FeedPresentation.Swipe.name)
    }

    @Test
    fun aStoredNameIsReadBackAsTheSameMode() {
        // La garantie qui compte : l'application rouvre dans le mode quitté.
        FeedPresentation.entries.forEach { mode ->
            assertEquals(mode, FeedPresentation.fromStoredName(mode.name))
        }
    }

    @Test
    fun nothingStoredMeansTheDefaultMode() {
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName(null))
    }

    @Test
    fun anUnknownStoredNameFallsBackToTheDefaultMode() {
        // Un fichier de préférences abîmé, restauré d'une sauvegarde, ou écrit
        // par une version qui connaissait un mode retiré depuis : aucun de ces
        // cas ne doit empêcher l'application de démarrer.
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName("Carrousel"))
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName(""))
    }

    @Test
    fun theStoredNameIsCaseSensitiveRatherThanApproximated() {
        // Deviner une correspondance approchée reviendrait à accepter une
        // valeur que l'application n'a jamais écrite : mieux vaut retomber sur
        // le défaut, visible, qu'inventer une intention.
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName("swipe"))
    }
}
