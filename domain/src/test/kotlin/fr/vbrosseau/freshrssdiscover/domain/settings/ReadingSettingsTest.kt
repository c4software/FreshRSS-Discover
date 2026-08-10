package fr.vbrosseau.freshrssdiscover.domain.settings

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bounds are observable nowhere else: in use, an out-of-bounds value
 * causes no error, only marking that never triggers or always triggers. Each
 * boundary is therefore probed from both sides.
 */
class ReadingSettingsTest {
    @Test
    fun theDefaultsAreThoseOfTheSpecification() {
        // SPECS.md §4.5: at least 60% of the displayed height, for at least 1
        // continuous second.
        assertEquals(0.6f, ReadingSettings.Default.visibleFraction)
        assertEquals(1_000L, ReadingSettings.Default.continuousVisibilityMillis)
    }

    /**
     * Guard against divergence.
     *
     * `ReadDetector` carries its own defaults as private constants, out of
     * reach of a read. Comparing behaviors is the only way to confirm that
     * [ReadingSettings.Default] says what the detector does: if either
     * changes alone, this test fails instead of letting the settings screen
     * lie.
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

        // Just under each boundary, then exactly on it: two differently tuned
        // detectors could not return the same answers.
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
        // Below it, the surface threshold no longer filters an article barely
        // shown at the screen edge: the dual threshold of SPECS.md §4.5
        // collapses to one.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = 0.19f) }
    }

    @Test
    fun aNegativeVisibleFractionIsRejected() {
        // Any observed fraction would exceed it: every article appearing on
        // screen would instantly become read.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = -0.1f) }
    }

    @Test
    fun aVisibleFractionAboveOneIsRejected() {
        // The caller bounds the fraction to the visible share of the screen:
        // above 1.0, no article would ever become read.
        assertFailsWith<IllegalArgumentException> { settings(visibleFraction = 1.01f) }
    }

    @Test
    fun aNotANumberVisibleFractionIsRejected() {
        // `NaN` compares to nothing: the threshold would become unreachable
        // without any value looking abnormal.
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
        // Beyond it, normal scrolling would never reach the threshold and
        // marking would appear broken.
        assertFailsWith<IllegalArgumentException> { settings(millis = 5_001L) }
    }

    @Test
    fun aNegativeContinuousVisibilityIsRejected() {
        // The duration condition would be satisfied on the first observation.
        assertFailsWith<IllegalArgumentException> { settings(millis = -1L) }
    }

    @Test
    fun automaticMarkingIsOnUntilSomeoneTurnsItOff() {
        // SPECS.md §1: reading is scrolling. An existing installation must
        // see nothing change on update.
        assertTrue(ReadingSettings.Default.autoMarkAsReadEnabled)
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
        // `coerceIn` would let `NaN` through: none of its comparisons is
        // true, and the value would come out unchanged.
        assertEquals(0.6f, ReadingSettings.coerced(Float.NaN, 1_000L).visibleFraction)
    }

    @Test
    fun coercingCarriesTheAutomaticMarkingSwitchThrough() {
        // The setting has no bounds but takes the same disk read-back path:
        // if it did not, a recorded off state would come back on at the next
        // launch.
        assertFalse(ReadingSettings.coerced(0.6f, 1_000L, autoMarkAsReadEnabled = false).autoMarkAsReadEnabled)
        assertTrue(ReadingSettings.coerced(0.6f, 1_000L, autoMarkAsReadEnabled = true).autoMarkAsReadEnabled)
    }

    @Test
    fun coercingAssumesAutomaticMarkingWhenNothingSaysOtherwise() {
        // The case of a preferences file written before the setting existed:
        // no value means enabled.
        assertTrue(ReadingSettings.coerced(0.6f, 1_000L).autoMarkAsReadEnabled)
    }

    @Test
    fun turningAutomaticMarkingOffLeavesTheThresholdsUntouched() {
        // The thresholds stay stored while the switch is off: they are shown
        // grayed out, not forgotten, and return when it is turned back on.
        val off = ReadingSettings.Default.copy(autoMarkAsReadEnabled = false)

        assertEquals(ReadingSettings.Default.visibleFraction, off.visibleFraction)
        assertEquals(ReadingSettings.Default.continuousVisibilityMillis, off.continuousVisibilityMillis)
    }

    private fun settings(
        visibleFraction: Float = ReadingSettings.Default.visibleFraction,
        millis: Long = ReadingSettings.Default.continuousVisibilityMillis,
    ) = ReadingSettings(visibleFraction = visibleFraction, continuousVisibilityMillis = millis)

    private companion object {
        val ARTICLE = ArticleId(1L)
    }
}
