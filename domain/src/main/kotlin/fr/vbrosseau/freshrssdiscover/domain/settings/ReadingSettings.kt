package fr.vbrosseau.freshrssdiscover.domain.settings

/**
 * The two thresholds of automatic marking (SPECS.md §4.5), in domain units: a
 * displayed-height fraction and a duration in milliseconds.
 *
 * This type exists to eliminate independent copies of these values (private
 * constants of `ReadDetector`, constants of `SettingsViewModel`, literals in
 * tests). Nothing prevented the copies from diverging, and divergence would
 * have been silent: the settings screen would keep announcing "60% for 1 s"
 * while the detector applied other values. A setting that displays something
 * other than what is applied is worse than no setting. [Default] is now the
 * single declaration on the settings side, and `ReadingSettingsTest` verifies
 * it produces the same behavior as the defaults compiled into `ReadDetector`.
 *
 * Bounds are checked at construction rather than left to the caller. The
 * source of an out-of-bounds value is not the UI (a stepped slider cannot
 * produce one) but the disk: a preferences file written by an earlier
 * version, truncated, or modified. See [coerced], the intended path for that
 * case.
 */
data class ReadingSettings(
    val visibleFraction: Float,
    val continuousVisibilityMillis: Long,
    /**
     * Whether visibility-based marking is active (SPECS.md §4.5).
     *
     * When off, only visibility detection stops: opening an article still
     * marks it read (SPECS.md §4.7), because that is a deliberate gesture and
     * not a scrolling side effect. Conflating the two would bring back into
     * the feed the article just read elsewhere.
     *
     * It lives here rather than in its own flow: it has the same reader as
     * the two thresholds (the read detector) and is read at the same moment.
     * Two sources would force a consumer of one to observe both.
     *
     * No bound to check, unlike the thresholds: a boolean has no aberrant
     * value. It still goes through [coerced], the single disk read-back path.
     */
    val autoMarkAsReadEnabled: Boolean = true,
) {
    init {
        require(visibleFraction in VisibleFractionRange) {
            "fraction visible hors bornes : $visibleFraction"
        }
        require(continuousVisibilityMillis in ContinuousVisibilityRange) {
            "durée de visibilité continue hors bornes : $continuousVisibilityMillis"
        }
    }

    companion object {
        /**
         * Required height fraction, between 20% and 100% inclusive.
         *
         * The ceiling is 1.0 and no more: SPECS.md §4.5 states the caller
         * clamps the fraction to the visible share of the screen, i.e. 1.0. A
         * threshold of 2.0 would never be reached and no article would ever
         * become read, with nothing signalling it.
         *
         * The floor is 0.2 and not 0.0: at zero the surface condition is
         * always true, and a negative fraction would even mark read any
         * article merely present in the observation. The surface threshold
         * exists precisely to exclude the article grazing the screen edge
         * (SPECS.md §4.5); below 20% it filters nothing and the double
         * threshold degrades to a single one.
         */
        val VisibleFractionRange: ClosedFloatingPointRange<Float> = 0.2f..1.0f

        /**
         * Required continuous display duration, between 150 ms and 5 s
         * inclusive.
         *
         * The floor was 1 s, the SPECS.md §4.5 value, and measurement on a
         * real device showed it unusable for the gesture the Discover feed is
         * built around. Sampling raw visibility at 5 Hz while scrolling
         * continuously: 63 articles crossed the screen, 1 was marked read.
         * 54 of the 62 lost had reached the surface threshold — most filled
         * the viewport entirely — and failed on duration alone, each being
         * fully visible for a single 200 ms sample. The setting could not
         * compensate, since 1 s was also the lowest value it offered.
         *
         * The floor is therefore 150 ms, just under one sampling period
         * (the screen samples visibility every 200 ms): the shortest value
         * that still requires two consecutive observations, hence a genuine
         * presence on screen rather than a single sample caught in flight.
         * Below one period the second threshold would collapse — the first
         * observation would satisfy it — and articles merely crossed by a
         * fling would become read, exactly what the double threshold exists
         * to exclude.
         *
         * The ceiling is 5 s because beyond it, in normal scrolling, no
         * article would ever reach the threshold: the setting would then be
         * indistinguishable from broken marking.
         */
        val ContinuousVisibilityRange: LongRange = 150L..5_000L

        /**
         * Values applied while nothing is stored.
         *
         * They must stay identical to the `ReadDetector` defaults: the only
         * guarantee that a fresh install applies what the settings screen
         * displays.
         *
         * The duration is 200 ms rather than the 1 s of SPECS.md §4.5. One
         * sampling period is what separates "seen while scrolling" from
         * "crossed the screen": at 200 ms an article must be present in two
         * consecutive observations, which a fling does not produce, while the
         * former default demanded five and missed almost everything the user
         * actually read (see [ContinuousVisibilityRange]).
         */
        val Default: ReadingSettings =
            ReadingSettings(
                visibleFraction = 0.6f,
                continuousVisibilityMillis = 200L,
                /*
                 * Enabled: the current behavior, the one SPECS.md §1
                 * describes. An existing installation must see nothing change.
                 */
                autoMarkAsReadEnabled = true,
            )

        /**
         * Clamps arbitrary values into bounds, without failing.
         *
         * Reserved for disk read-back: a corrupted preference must not prevent
         * the app from starting, whereas an out-of-bounds call from the UI is
         * a programming defect and must throw.
         *
         * `NaN` falls back to the default rather than a bound: it compares to
         * nothing, so `coerceIn` would let it through unchanged, and a `NaN`
         * threshold would make every comparison false; no article would ever
         * be marked read again.
         *
         * [autoMarkAsReadEnabled] passes through uncorrected (a boolean has no
         * out-of-bounds value), but this remains the single disk read-back
         * path: bypassing it would require each caller to know which of the
         * three settings is corrected and which is not.
         */
        fun coerced(
            visibleFraction: Float,
            continuousVisibilityMillis: Long,
            autoMarkAsReadEnabled: Boolean = Default.autoMarkAsReadEnabled,
        ): ReadingSettings =
            ReadingSettings(
                visibleFraction =
                    if (visibleFraction.isNaN()) {
                        Default.visibleFraction
                    } else {
                        visibleFraction.coerceIn(VisibleFractionRange)
                    },
                continuousVisibilityMillis =
                    continuousVisibilityMillis.coerceIn(
                        ContinuousVisibilityRange.first,
                        ContinuousVisibilityRange.last,
                    ),
                autoMarkAsReadEnabled = autoMarkAsReadEnabled,
            )
    }
}
