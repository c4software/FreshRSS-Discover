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
    const val OFFLINE_BANNER = "discover:offline-banner"
    const val OFFLINE_NOTICE = "discover:offline-notice"
    const val OFFLINE_NOTICE_DISMISS = "discover:offline-notice-dismiss"
    const val STALE_NOTICE = "discover:stale-notice"
    const val STALE_NOTICE_REFRESH = "discover:stale-notice-refresh"
    const val STALE_NOTICE_DISMISS = "discover:stale-notice-dismiss"

    fun card(id: Long) = "discover:card:$id"

    fun share(id: Long) = "discover:share:$id"
}
