package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedPresentationTest {
    @Test
    fun theListIsTheModeAFreshInstallationOpensIn() {
        // SPECS.md §4.8: the default mode, chosen because it shows what the
        // feed contains before requiring a gesture.
        assertEquals(FeedPresentation.List, FeedPresentation.Default)
    }

    @Test
    fun eachModeIsStoredUnderItsOwnName() {
        // These strings are written to disk: renaming them would break
        // reading back existing preferences, and this test makes that
        // visible.
        assertEquals("List", FeedPresentation.List.name)
        assertEquals("Immersive", FeedPresentation.Immersive.name)
    }

    @Test
    fun aStoredNameIsReadBackAsTheSameMode() {
        // The guarantee that matters: the app reopens in the mode it left.
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
        // A damaged preferences file, one restored from backup, or one
        // written by a version that knew a since-removed mode: none of these
        // may prevent the app from starting.
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName("Carrousel"))
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName(""))
    }

    @Test
    fun theRemovedSwipeModeIsNotCarriedOver() {
        // GOAL-038 replaced Swipe without a mapping, by the author's ruling:
        // an installation left in Swipe reopens in the default mode.
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName("Swipe"))
    }

    @Test
    fun theStoredNameIsCaseSensitiveRatherThanApproximated() {
        // Guessing an approximate match would mean accepting a value the app
        // never wrote: better to fall back to the visible default than to
        // invent an intent.
        assertEquals(FeedPresentation.Default, FeedPresentation.fromStoredName("immersive"))
    }
}
