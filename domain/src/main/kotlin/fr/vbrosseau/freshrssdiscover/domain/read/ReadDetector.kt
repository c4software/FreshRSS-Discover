package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.Clock

/** Displayed-height fraction above which an article counts as seen (SPECS.md §4.5). */
private const val DEFAULT_VISIBLE_FRACTION_THRESHOLD = 0.6f

/**
 * Continuous display duration required before concluding a read.
 *
 * 200 ms, where SPECS.md §4.5 says one second. The second was measured
 * unusable for a feed one scrolls: see `ReadingSettings.ContinuousVisibilityRange`,
 * which carries the measurement and must keep the same value as this one.
 */
private const val DEFAULT_CONTINUOUS_VISIBILITY_MILLIS = 200L

/**
 * Decides when an article becomes read based on its on-screen visibility.
 *
 * SPECS.md §4.5 sets a double threshold: a minimum share of the height
 * displayed, held for a minimum continuous duration. Both are necessary and
 * neither suffices: surface alone would mark articles crossed by a fast
 * scroll, duration alone would mark an article barely peeking at the screen
 * edge.
 *
 * Both values are named parameters: SPECS.md states they will be tuned with
 * usage, and scattered constants could not be exposed in the settings
 * (SPECS.md §6).
 *
 * The component is pure apart from [clock]: it does not read the system time,
 * triggers no effect, and knows neither Android nor the network. This makes
 * the rule testable to the millisecond.
 *
 * Not thread-safe: designed to be called from the loop observing the list,
 * i.e. always from the same thread.
 */
class ReadDetector(
    private val clock: Clock,
    private val visibleFractionThreshold: Float = DEFAULT_VISIBLE_FRACTION_THRESHOLD,
    private val continuousVisibilityMillis: Long = DEFAULT_CONTINUOUS_VISIBILITY_MILLIS,
) {
    /**
     * Instant at which each article crossed the surface threshold without
     * dropping below it since.
     *
     * Entries for articles absent from the observation are purged on every
     * call: otherwise a scrolling session would leave one entry per article
     * never seen again.
     */
    private val visibleSince = mutableMapOf<ArticleId, Long>()

    /**
     * Articles already reported.
     *
     * Kept for the detector's whole lifetime: it is the only way to guarantee
     * an article is never reported twice. An article stays visible for dozens
     * of render frames after crossing the threshold, and re-reporting it would
     * produce as many useless network calls. The cost is bounded by what the
     * user actually read, one id per article, not by the number of
     * observations.
     */
    private val reported = mutableSetOf<ArticleId>()

    /**
     * Processes a new visibility observation.
     *
     * [visibility] describes the articles currently on screen and the fraction
     * of their height displayed. An article absent from the map is considered
     * off screen: its timer is dropped, and it restarts from zero if it comes
     * back.
     *
     * Both thresholds are inclusive: SPECS.md §4.5 says "at least 60%" and "at
     * least 1 second", so exactly 60.0% for exactly 1000 ms qualifies. An
     * exclusive threshold would also make the rule depend on how the UI rounds
     * the fraction, where 0.6 is never exactly representable.
     *
     * @return only the articles that crossed the threshold during this call,
     *   never those already reported.
     */
    fun onVisibilityChanged(visibility: Map<ArticleId, Float>): Set<ArticleId> {
        val now = clock.nowEpochMillis()
        visibleSince.keys.retainAll(visibility.keys)
        val justRead =
            visibility
                .filterKeys { it !in reported }
                .filter { (id, fraction) -> hasReachedThreshold(id, fraction, now) }
                .keys
                .toSet()
        reported += justRead
        // The timer of a reported article no longer serves a purpose: keeping
        // it would grow the map for as long as the article stays on screen.
        visibleSince.keys.removeAll(justRead)
        return justRead
    }

    /**
     * Times an article's visibility and reports whether it just reached the
     * required duration.
     *
     * Dropping below the surface threshold clears the timer instead of pausing
     * it: "continuous" is the very condition SPECS.md §4.5 states. Without the
     * reset, ten 100 ms passes would accumulate a second, which is exactly the
     * fast scroll the duration threshold rules out.
     */
    private fun hasReachedThreshold(
        id: ArticleId,
        fraction: Float,
        now: Long,
    ): Boolean {
        if (fraction < visibleFractionThreshold) {
            visibleSince.remove(id)
            return false
        }
        val since = visibleSince.getOrPut(id) { now }
        return now - since >= continuousVisibilityMillis
    }
}
