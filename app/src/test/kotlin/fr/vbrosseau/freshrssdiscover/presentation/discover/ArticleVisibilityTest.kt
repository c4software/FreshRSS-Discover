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

/** Reference viewport height in pixels: a round value keeps fractions readable. */
private const val VIEWPORT_HEIGHT = 1_000

/**
 * One period of clock advance, plus one millisecond.
 *
 * `advanceTimeBy` is exclusive of the reached instant: advancing by exactly
 * one period would not run the task scheduled at that precise instant, and the
 * test would count one observation fewer than actually occurs.
 */
private const val ONE_PERIOD = VISIBILITY_SAMPLING_PERIOD_MILLIS + 1

/** Fraction measured in a standard viewport, to keep test cases light. */
private fun fractionOf(
    offset: Int,
    size: Int,
    viewportStart: Int = 0,
    viewportEnd: Int = VIEWPORT_HEIGHT,
): Float = visibleFraction(offset, size, viewportStart, viewportEnd)

class ArticleVisibilityTest {

    // ----- Visible fraction ---------------------------------------------------

    @Test
    fun anArticleEntirelyInsideTheViewportIsFullyVisible() {
        assertEquals(1f, fractionOf(offset = 100, size = 400))
    }

    @Test
    fun anArticleCutByTheTopEdgeCountsOnlyWhatRemainsBelowIt() {
        // Top half off screen: 200 px visible out of 400.
        assertEquals(0.5f, fractionOf(offset = -200, size = 400))
    }

    @Test
    fun anArticleCutByTheBottomEdgeCountsOnlyWhatRemainsAboveIt() {
        assertEquals(0.25f, fractionOf(offset = 900, size = 400))
    }

    @Test
    fun anArticleTallerThanTheViewportIsFullyVisibleOnceItFillsIt() {
        // SPECS.md §4.5: the reference is the visible share of the screen, not
        // the article's own height. Measured against itself, this article would
        // cap at 40% and thus never become read.
        assertEquals(1f, fractionOf(offset = -600, size = 2_500))
    }

    @Test
    fun anArticleTallerThanTheViewportIsMeasuredAgainstTheViewportWhileItEnters() {
        // 300 px of its 2,500 are on screen: 30% of the viewport, thus below
        // the threshold, where measuring against its own height would give 12%.
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
        // A `LazyColumn` with content padding has a negative `viewportStartOffset`:
        // ignoring that origin would shift every fraction.
        assertEquals(1f, fractionOf(offset = -100, size = 400, viewportStart = -100, viewportEnd = 900))
    }

    @Test
    fun anArticleWithoutHeightHasNoDefinedFraction() {
        // Zero guards against a NaN reaching the detector, which would then
        // compare false against every threshold without anything reporting it.
        assertEquals(0f, fractionOf(offset = 0, size = 0))
    }

    @Test
    fun aViewportNotYetMeasuredYieldsNoVisibility() {
        assertEquals(0f, fractionOf(offset = 0, size = 400, viewportEnd = 0))
    }

    // ----- Periodic sampling --------------------------------------------------

    @Test
    fun visibilityIsObservedOnceImmediatelyThenAtEachPeriod() = runTest {
        // Without this cadence, a motionless article would produce a single
        // observation and its timer would never reach the one-second mark.
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
        // The maximum crossing delay is one period: keeping it under a quarter
        // of the 1 s threshold (SPECS.md §4.5) keeps the gap invisible in use.
        // This is what forbids loosening it to save cycles.
        assertTrue(VISIBILITY_SAMPLING_PERIOD_MILLIS <= 250L)

        val observations = mutableListOf<Map<ArticleId, Float>>()
        val sampling = startSampling(observations)

        advanceTimeBy(1_001L)

        assertTrue(observations.size >= 5, "une seconde doit produire au moins cinq observations")
        sampling.cancel()
    }

    @Test
    fun cancellingTheSamplingStopsTheObservations() = runTest {
        // This is how going to the background stops the measurement:
        // `repeatOnLifecycle` cancels the coroutine carrying this loop.
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
        // The layout changes without notice: capturing the reading once would
        // keep timing an article that has already left the screen.
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
 * Arms the sampling loop on the test thread.
 *
 * `UNDISPATCHED` starts it without waiting for the test to yield: the
 * immediate observation, the one preceding the first wait, is part of the
 * verified behavior, and a deferred start would make it invisible.
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
