package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La règle de SPECS.md §4.5 n'est observable qu'en conditions réelles de
 * défilement, où elle est impossible à vérifier à la milliseconde. Ces tests
 * sont donc le seul endroit où le double seuil est réellement constaté :
 * l'horloge pilotée permet d'attaquer chaque frontière des deux côtés.
 */
class ReadDetectorTest {
    private val clock = FakeClock()
    private val detector = ReadDetector(clock)

    private val first = ArticleId(1L)
    private val second = ArticleId(2L)

    @Test
    fun anArticleBelowTheSurfaceThresholdIsNeverRead() {
        // Un article à moitié visible en bord d'écran ne doit pas se marquer
        // lu, même laissé là longtemps.
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 0.5f)))
        clock.advanceBy(10_000L)

        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 0.5f)))
    }

    @Test
    fun anArticleAboveTheSurfaceThresholdButTooBriefIsNotRead() {
        // Le défilement rapide : la surface est atteinte, la durée non.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(999L)

        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun anArticleVisibleEnoughForLongEnoughBecomesRead() {
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(1_000L)

        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun exactlyTheSurfaceThresholdCounts() {
        // Le seuil est inclusif : SPECS.md §4.5 dit « au moins 60 % ».
        detector.onVisibilityChanged(mapOf(first to 0.6f))
        clock.advanceBy(1_000L)

        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 0.6f)))
    }

    @Test
    fun justBelowTheSurfaceThresholdDoesNotCount() {
        detector.onVisibilityChanged(mapOf(first to 0.599f))
        clock.advanceBy(1_000L)

        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 0.599f)))
    }

    @Test
    fun exactlyTheDurationCounts() {
        // Même raisonnement : « au moins 1 seconde », donc 1000 ms suffisent.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(999L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(1L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun aVisibilityInterruptedBeforeTheDurationRestartsFromZero() {
        // Sans remise à zéro, dix passages de 100 ms cumuleraient la seconde —
        // exactement le défilement rapide que le seuil de durée écarte.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(900L)
        detector.onVisibilityChanged(mapOf(first to 0.1f))

        clock.advanceBy(100L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(999L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(1L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun anArticleLeavingTheScreenBeforeTheThresholdIsForgotten() {
        // Sortir de l'observation vaut interruption : au retour, le chronomètre
        // repart de zéro.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(900L)
        detector.onVisibilityChanged(emptyMap())

        clock.advanceBy(900L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(1_000L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun anArticleThatLeftTheScreenLeavesNoInternalState() {
        // Une session de défilement traverse des milliers d'articles : chacun
        // laisserait une entrée derrière lui.
        detector.onVisibilityChanged(mapOf(first to 1.0f, second to 0.8f))
        assertEquals(2, detector.trackedArticleCount)

        detector.onVisibilityChanged(emptyMap())

        assertEquals(0, detector.trackedArticleCount)
    }

    @Test
    fun aReportedArticleLeavesNoInternalStateEither() {
        // Une fois signalé, le chronomètre n'a plus d'objet : le garder tant
        // que l'article reste à l'écran ferait grossir la table pour rien.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(1_000L)
        detector.onVisibilityChanged(mapOf(first to 1.0f))

        assertEquals(0, detector.trackedArticleCount)
    }

    @Test
    fun anArticleIsNeverReportedTwice() {
        // Un article reste visible pendant des dizaines d'images de rendu après
        // avoir franchi le seuil : le re-signaler produirait autant d'appels
        // réseau inutiles.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(1_000L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(1_000L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun anArticleScrolledBackIntoViewIsNotReportedAgain() {
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(1_000L)
        detector.onVisibilityChanged(mapOf(first to 1.0f))

        detector.onVisibilityChanged(emptyMap())
        clock.advanceBy(5_000L)
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(5_000L)

        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun severalArticlesAreTrackedIndependently() {
        // Deux articles peuvent tenir à l'écran ; leurs chronomètres démarrent à
        // des instants différents.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(600L)
        detector.onVisibilityChanged(mapOf(first to 1.0f, second to 0.7f))

        clock.advanceBy(400L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f, second to 0.7f)))

        clock.advanceBy(600L)
        assertEquals(setOf(second), detector.onVisibilityChanged(mapOf(first to 1.0f, second to 0.7f)))
    }

    @Test
    fun severalArticlesCanBecomeReadInTheSameObservation() {
        // Cas d'un défilement stoppé net : les deux chronomètres arrivent à
        // échéance ensemble, et le lot doit les contenir tous les deux.
        detector.onVisibilityChanged(mapOf(first to 0.6f, second to 1.0f))
        clock.advanceBy(1_000L)

        assertEquals(
            setOf(first, second),
            detector.onVisibilityChanged(mapOf(first to 0.6f, second to 1.0f)),
        )
    }

    @Test
    fun customThresholdsReplaceTheDefaults() {
        // SPECS.md §4.5 annonce que ces valeurs seront ajustées à l'usage : un
        // réglage plus exigeant doit se substituer entièrement au défaut.
        val strict = ReadDetector(clock, visibleFractionThreshold = 0.9f, continuousVisibilityMillis = 3_000L)

        strict.onVisibilityChanged(mapOf(first to 0.8f))
        clock.advanceBy(3_000L)
        assertEquals(emptySet(), strict.onVisibilityChanged(mapOf(first to 0.8f)))

        strict.onVisibilityChanged(mapOf(first to 0.95f))
        clock.advanceBy(2_999L)
        assertEquals(emptySet(), strict.onVisibilityChanged(mapOf(first to 0.95f)))

        clock.advanceBy(1L)
        assertEquals(setOf(first), strict.onVisibilityChanged(mapOf(first to 0.95f)))
    }

    @Test
    fun aLenientThresholdMarksSoonerThanTheDefault() {
        // L'autre sens : un réglage plus permissif que le défaut doit marquer
        // là où le détecteur par défaut ne marquerait pas.
        val lenient = ReadDetector(clock, visibleFractionThreshold = 0.2f, continuousVisibilityMillis = 100L)

        lenient.onVisibilityChanged(mapOf(first to 0.3f))
        clock.advanceBy(100L)

        assertEquals(setOf(first), lenient.onVisibilityChanged(mapOf(first to 0.3f)))
        assertTrue(detector.onVisibilityChanged(mapOf(first to 0.3f)).isEmpty())
    }

    @Test
    fun anEmptyObservationReportsNothing() {
        assertEquals(emptySet(), detector.onVisibilityChanged(emptyMap()))
    }

    @Test
    fun aSingleObservationNeverSufficesHoweverVisible() {
        // Le premier appel ne fait que démarrer le chronomètre : conclure dès
        // là marquerait tout l'écran au premier rendu.
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))
        assertEquals(1, detector.trackedArticleCount)
    }
}
