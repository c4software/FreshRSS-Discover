package fr.vbrosseau.freshrssdiscover.presentation.navigation

/** Application routes. */
object AppRoutes {
    const val DISCOVER = "discover"
    const val SETTINGS = "settings"

    /**
     * Reading statistics (SPECS.md §6), reached from the settings screen.
     *
     * Not an [AppDestination]: the bar carries the top-level destinations,
     * and this screen is a detail of the settings — it is left with back.
     */
    const val STATS = "stats"
}
