package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule from SPECS.md §4.5 is only observable under real scrolling
 * conditions, where it cannot be verified to the millisecond. These tests are
 * therefore the only place the dual threshold is actually checked: the
 * controlled clock allows probing each boundary from both sides.
 */
class ReadDetectorTest {
    private val clock = FakeClock()
    private val detector = ReadDetector(clock)

    private val first = ArticleId(1L)
    private val second = ArticleId(2L)

    @Test
    fun anArticleBelowTheSurfaceThresholdIsNeverRead() {
        // An article half visible at the screen edge must not be marked read,
        // even if left there for a long time.
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 0.5f)))
        clock.advanceBy(10_000L)

        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 0.5f)))
    }

    @Test
    fun anArticleAboveTheSurfaceThresholdButTooBriefIsNotRead() {
        // Fast scrolling: the surface threshold is met, the duration is not.
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
        // The threshold is inclusive: SPECS.md §4.5 says "at least 60%".
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
        // Same reasoning: "at least 1 second", so 1000 ms suffice.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(999L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(1L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun aVisibilityInterruptedBeforeTheDurationRestartsFromZero() {
        // Without a reset, ten 100 ms passes would accumulate the second,
        // exactly the fast scrolling the duration threshold rules out.
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
        // Leaving observation counts as an interruption: on return, the timer
        // restarts from zero.
        detector.onVisibilityChanged(mapOf(first to 1.0f))
        clock.advanceBy(900L)
        detector.onVisibilityChanged(emptyMap())

        clock.advanceBy(900L)
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))

        clock.advanceBy(1_000L)
        assertEquals(setOf(first), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }

    @Test
    fun anArticleIsNeverReportedTwice() {
        // An article stays visible for dozens of rendered frames after
        // crossing the threshold: re-reporting it would produce as many
        // useless network calls.
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
        // Two articles can fit on screen; their timers start at different
        // instants.
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
        // Scrolling stopped abruptly: both timers expire together, and the
        // batch must contain both.
        detector.onVisibilityChanged(mapOf(first to 0.6f, second to 1.0f))
        clock.advanceBy(1_000L)

        assertEquals(
            setOf(first, second),
            detector.onVisibilityChanged(mapOf(first to 0.6f, second to 1.0f)),
        )
    }

    @Test
    fun customThresholdsReplaceTheDefaults() {
        // SPECS.md §4.5 states these values will be tuned through use: a
        // stricter setting must fully replace the default.
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
        // The other direction: a setting more permissive than the default
        // must mark where the default detector would not.
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
        // The first call only starts the timer: concluding there would mark
        // the whole screen on the first render.
        assertEquals(emptySet(), detector.onVisibilityChanged(mapOf(first to 1.0f)))
    }
}
