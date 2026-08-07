package fr.vbrosseau.freshrssdiscover.presentation.navigation

/** Repères de test de la barre de navigation. */
object NavigationTestTags {
    fun item(destination: AppDestination) = "nav:${destination.route}"
}
