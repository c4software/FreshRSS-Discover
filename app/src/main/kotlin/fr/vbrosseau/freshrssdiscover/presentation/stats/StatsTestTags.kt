package fr.vbrosseau.freshrssdiscover.presentation.stats

/** Test tags for the statistics screen. */
object StatsTestTags {
    const val CHART = "stats:chart"
    const val EMPTY = "stats:empty"
    const val DOMINANT = "stats:dominant"
    const val LEARNING = "stats:learning"

    /** One tag per bar, suffixed by its hour: bars differ only by height. */
    fun barOf(hour: Int): String = "stats:bar-$hour"
}
