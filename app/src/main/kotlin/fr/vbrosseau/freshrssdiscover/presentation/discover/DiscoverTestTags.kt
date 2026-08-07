package fr.vbrosseau.freshrssdiscover.presentation.discover

/** Repères de test de l'écran Discover. */
object DiscoverTestTags {
    const val LIST = "discover:list"
    const val EMPTY = "discover:empty"
    const val END_OF_FEED = "discover:end"
    const val FAILURE = "discover:failure"
    const val RETRY = "discover:retry"
    const val ILLUSTRATION = "discover:illustration"
    const val NO_LINK = "discover:no-link"

    fun card(id: Long) = "discover:card:$id"
}
