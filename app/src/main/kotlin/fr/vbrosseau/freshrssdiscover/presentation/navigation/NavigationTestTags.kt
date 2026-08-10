package fr.vbrosseau.freshrssdiscover.presentation.navigation

/** Test tags for the navigation bar. */
object NavigationTestTags {
    fun item(destination: AppDestination) = "nav:${destination.route}"
}
