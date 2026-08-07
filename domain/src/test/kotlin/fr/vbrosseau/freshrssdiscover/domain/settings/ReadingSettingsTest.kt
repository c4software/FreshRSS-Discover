package fr.vbrosseau.freshrssdiscover.domain.settings

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Les bornes ne se constatent nulle part ailleurs : à l'usage, une valeur hors
 * bornes ne provoque pas d'erreur mais un marquage qui ne se déclenche jamais,
 * ou qui se déclenche toujours. Chaque frontière est donc attaquée des **deux**
 * côtés.
 */
class ReadingSettingsTest {
    @Test
    fun theDefaultsAreThoseOfTheSpecification() {
        // SPECS.md §4.5 : au moins 60 % de la hauteur affichée, pendant au
        // moins 1 seconde continue.
        assertEquals(0.6f, ReadingSettings.Default.visibleFraction)
        assertEquals(1_000L, ReadingSettings.Default.continuousVisibilityMillis)
    }

    /**
     * Le garde-fou contre la divergence.
     *
     * `ReadDetector` porte ses propres défauts en constantes privées, hors de
     * portée d'une lecture. Comparer les **comportements** est la seule façon
     * de constater que [ReadingSettings.Default] dit bien ce que le détecteur
     * fait : si l'un des deux change seul, ce test rougit au lieu de laisser
     * l'écran de réglages mentir.
     */
    @Test
    fun theDefaultsAgreeWithWhatTheReadDetectorActuallyApplies() {
        val compiledClock = FakeClock()
        val declaredClock = FakeClock()
        val compiled = ReadDetector(compiledClock)
        val declared =
            ReadDetector(
                clock = declaredClock,
                visibleFractionThreshold = ReadingSettings.Default.visibleFraction,
                continuousVisibilityMillis = ReadingSettings.Default.continuousVisibilityMillis,
            )

        // Juste sous chaque frontière, puis exactement dessus : deux détecteurs
        // réglés différemment ne pourraient pas rendre les mêmes réponses.
        val observations = listOf(0.59f to 0L, 0.6f to 0L, 0.6f to 999L, 0.6f to 1L)
        val fromCompiled =
            observations.map { (fraction, elapsed) ->
                compiledClock.advanceBy(elapsed)
                compiled.onVisibilityChanged(mapOf(ARTICLE to fraction))
            }
        val fromDeclared =
            observations.map { (fraction, elapsed) ->
                declaredClock.advanceBy(elapsed)
                declared.onVisibilityChanged(mapOf(ARTICLE to fraction))
            }

        assertEquals(fromCompiled, fromDeclared)
        assertTrue(ARTICLE in fromCompiled.last(), "le scénario n'atteint jamais le seuil")
    }

    @Test
    fun aVisibleFractionOnEitherBoundIsAccepted() {
        assertEquals(0.2f, settings(visibleFraction = 0.2f).visibleFraction)
        assertEquals(1.0f, settings(visibleFraction = 1.0f).visibleFraction)
    }

    @Test
    fun aVisibleFractionJustBelowTheLowerBoundIsRejected() {
        // En dessous, le seuil de surface ne filtre plus l'article effleuré en
        // bord d'écran : le double seuil de SPECS.md §4.5 se réduit à un seul.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = 0.19f) }
    }

    @Test
    fun aNegativeVisibleFractionIsRejected() {
        // Toute fraction observée lui serait supérieure : chaque article
        // apparaissant à l'écran deviendrait lu instantanément.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = -0.1f) }
    }

    @Test
    fun aVisibleFractionAboveOneIsRejected() {
        // L'appelant borne la fraction à la part visible de l'écran : au-delà
        // de 1.0, plus aucun article ne deviendrait jamais lu.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = 1.01f) }
    }

    @Test
    fun aNotANumberVisibleFractionIsRejected() {
        // `NaN` ne se compare à rien : le seuil deviendrait inatteignable sans
        // qu'aucune valeur ne paraisse aberrante.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = Float.NaN) }
    }

    @Test
    fun aContinuousVisibilityOnEitherBoundIsAccepted() {
        assertEquals(1_000L, settings(millis = 1_000L).continuousVisibilityMillis)
        assertEquals(5_000L, settings(millis = 5_000L).continuousVisibilityMillis)
    }

    @Test
    fun aContinuousVisibilityJustBelowTheLowerBoundIsRejected() {
        assertFailsWith<IllegalArgumentException> { settings(millis = 999L) }
    }

    @Test
    fun aContinuousVisibilityJustAboveTheUpperBoundIsRejected() {
        // Au-delà, un défilement normal n'atteindrait plus jamais le seuil et
        // le marquage paraîtrait en panne.
        assertFailsWith<IllegalArgumentException> { settings(millis = 5_001L) }
    }

    @Test
    fun aNegativeContinuousVisibilityIsRejected() {
        // La condition de durée serait satisfaite dès la première observation.
        assertFailsWith<IllegalArgumentException> { settings(millis = -1L) }
    }

    @Test
    fun coercingLeavesValuesAlreadyInRangeUntouched() {
        assertEquals(ReadingSettings.Default, ReadingSettings.coerced(0.6f, 1_000L))
    }

    @Test
    fun coercingBringsValuesBelowTheBoundsBackUp() {
        val coerced = ReadingSettings.coerced(visibleFraction = -5f, continuousVisibilityMillis = 0L)

        assertEquals(0.2f, coerced.visibleFraction)
        assertEquals(1_000L, coerced.continuousVisibilityMillis)
    }

    @Test
    fun coercingBringsValuesAboveTheBoundsBackDown() {
        val coerced = ReadingSettings.coerced(visibleFraction = 2f, continuousVisibilityMillis = 60_000L)

        assertEquals(1.0f, coerced.visibleFraction)
        assertEquals(5_000L, coerced.continuousVisibilityMillis)
    }

    @Test
    fun coercingReplacesNotANumberByTheDefaultRatherThanABound() {
        // `coerceIn` laisserait passer `NaN` : aucune de ses comparaisons n'est
        // vraie, et la valeur ressortirait telle quelle.
        assertEquals(0.6f, ReadingSettings.coerced(Float.NaN, 1_000L).visibleFraction)
    }

    private fun settings(
        visibleFraction: Float = ReadingSettings.Default.visibleFraction,
        millis: Long = ReadingSettings.Default.continuousVisibilityMillis,
    ) = ReadingSettings(visibleFraction = visibleFraction, continuousVisibilityMillis = millis)

    private companion object {
        val ARTICLE = ArticleId(1L)
    }
}
