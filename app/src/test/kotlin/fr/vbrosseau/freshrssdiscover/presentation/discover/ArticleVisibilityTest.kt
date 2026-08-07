package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hauteur de fenêtre de référence, en pixels : une valeur ronde rend les fractions lisibles. */
private const val VIEWPORT_HEIGHT = 1_000

/**
 * Un cran d'horloge d'une période, plus une milliseconde.
 *
 * `advanceTimeBy` est **exclusif** de l'instant atteint : avancer exactement
 * d'une période n'exécuterait pas la tâche programmée à cet instant précis, et
 * le test compterait une observation de moins qu'il n'en survient réellement.
 */
private const val ONE_PERIOD = VISIBILITY_SAMPLING_PERIOD_MILLIS + 1

/** Fraction mesurée dans une fenêtre standard, pour alléger les cas de test. */
private fun fractionOf(
    offset: Int,
    size: Int,
    viewportStart: Int = 0,
    viewportEnd: Int = VIEWPORT_HEIGHT,
): Float = visibleFraction(offset, size, viewportStart, viewportEnd)

class ArticleVisibilityTest {

    // ----- Fraction visible ---------------------------------------------------

    @Test
    fun anArticleEntirelyInsideTheViewportIsFullyVisible() {
        assertEquals(1f, fractionOf(offset = 100, size = 400))
    }

    @Test
    fun anArticleCutByTheTopEdgeCountsOnlyWhatRemainsBelowIt() {
        // Moitié haute hors écran : 200 px visibles sur 400.
        assertEquals(0.5f, fractionOf(offset = -200, size = 400))
    }

    @Test
    fun anArticleCutByTheBottomEdgeCountsOnlyWhatRemainsAboveIt() {
        assertEquals(0.25f, fractionOf(offset = 900, size = 400))
    }

    @Test
    fun anArticleTallerThanTheViewportIsFullyVisibleOnceItFillsIt() {
        // SPECS.md §4.5 : la référence est la part visible de l'écran, pas la
        // hauteur propre de l'article. Mesuré sur lui-même, cet article
        // plafonnerait à 40 % et ne deviendrait donc jamais lu.
        assertEquals(1f, fractionOf(offset = -600, size = 2_500))
    }

    @Test
    fun anArticleTallerThanTheViewportIsMeasuredAgainstTheViewportWhileItEnters() {
        // 300 px de ses 2 500 sont à l'écran : 30 % de la fenêtre, donc sous le
        // seuil, là où la mesure sur sa propre hauteur donnerait 12 %.
        assertEquals(0.3f, fractionOf(offset = 700, size = 2_500))
    }

    @Test
    fun anArticleAboveTheViewportIsNotVisibleAtAll() {
        assertEquals(0f, fractionOf(offset = -500, size = 400))
    }

    @Test
    fun anArticleBelowTheViewportIsNotVisibleAtAll() {
        assertEquals(0f, fractionOf(offset = 1_200, size = 400))
    }

    @Test
    fun theContentPaddingOfTheListShiftsTheViewportWithoutDistortingTheFraction() {
        // Une `LazyColumn` à marge de contenu a un `viewportStartOffset` négatif :
        // ignorer cette origine décalerait toutes les fractions.
        assertEquals(1f, fractionOf(offset = -100, size = 400, viewportStart = -100, viewportEnd = 900))
    }

    @Test
    fun anArticleWithoutHeightHasNoDefinedFraction() {
        // Le zéro protège d'un NaN transmis au détecteur, qui comparerait alors
        // faux à tous les seuils sans que rien ne le signale.
        assertEquals(0f, fractionOf(offset = 0, size = 0))
    }

    @Test
    fun aViewportNotYetMeasuredYieldsNoVisibility() {
        assertEquals(0f, fractionOf(offset = 0, size = 400, viewportEnd = 0))
    }

    // ----- Échantillonnage périodique -----------------------------------------

    @Test
    fun visibilityIsObservedOnceImmediatelyThenAtEachPeriod() = runTest {
        // Sans cette cadence, un article immobile ne produirait qu'une seule
        // observation et son chronomètre n'atteindrait jamais la seconde.
        val observations = mutableListOf<Map<ArticleId, Float>>()
        val sampling = startSampling(observations)

        assertEquals(1, observations.size)

        advanceTimeBy(ONE_PERIOD)
        assertEquals(2, observations.size)

        advanceTimeBy(VISIBILITY_SAMPLING_PERIOD_MILLIS * 3)
        assertEquals(5, observations.size)

        sampling.cancel()
    }

    @Test
    fun theSamplingPeriodIsFineEnoughToKeepTheOneSecondThresholdPrecise() = runTest {
        // Le retard maximal du franchissement vaut une période : la maintenir
        // sous le quart du seuil de 1 s (SPECS.md §4.5) garde l'écart invisible
        // à l'usage. C'est ce qui interdit de la relâcher « pour économiser ».
        assertTrue(VISIBILITY_SAMPLING_PERIOD_MILLIS <= 250L)

        val observations = mutableListOf<Map<ArticleId, Float>>()
        val sampling = startSampling(observations)

        advanceTimeBy(1_001L)

        assertTrue(observations.size >= 5, "une seconde doit produire au moins cinq observations")
        sampling.cancel()
    }

    @Test
    fun cancellingTheSamplingStopsTheObservations() = runTest {
        // C'est ainsi que le passage en arrière-plan interrompt la mesure :
        // `repeatOnLifecycle` annule la coroutine qui porte cette boucle.
        val observations = mutableListOf<Map<ArticleId, Float>>()
        val sampling = startSampling(observations)

        advanceTimeBy(ONE_PERIOD)
        val beforeCancel = observations.size
        sampling.cancel()

        advanceTimeBy(VISIBILITY_SAMPLING_PERIOD_MILLIS * 10)

        assertEquals(beforeCancel, observations.size)
    }

    @Test
    fun eachSampleReReadsTheLayoutRatherThanReplayingTheFirstOne() = runTest {
        // La disposition change sans prévenir : capturer le relevé une fois
        // ferait chronométrer un article qui a déjà quitté l'écran.
        val observations = mutableListOf<Map<ArticleId, Float>>()
        var fraction = 1f
        val sampling = startSampling(observations) { fraction }

        fraction = 0f
        advanceTimeBy(ONE_PERIOD)

        assertEquals(listOf(1f, 0f), observations.map { it.getValue(ArticleId(1L)) })
        sampling.cancel()
    }
}

/**
 * Arme la boucle d'échantillonnage sur le fil du test.
 *
 * `UNDISPATCHED` la fait démarrer sans attendre que le test rende la main :
 * l'observation immédiate — celle qui précède la première attente — fait partie
 * du comportement vérifié, et un démarrage différé la rendrait invisible.
 */
private fun TestScope.startSampling(
    observations: MutableList<Map<ArticleId, Float>>,
    fraction: () -> Float = { 1f },
): Job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
    sampleVisibility(
        visibility = { mapOf(ArticleId(1L) to fraction()) },
        onVisibilityChanged = { observations += it },
    )
}
