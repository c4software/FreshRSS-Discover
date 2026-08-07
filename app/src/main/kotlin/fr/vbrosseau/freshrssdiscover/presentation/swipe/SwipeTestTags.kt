package fr.vbrosseau.freshrssdiscover.presentation.swipe

/** Repères de test de la vue Balayage. */
object SwipeTestTags {
    const val PAGER = "swipe:pager"
    const val EMPTY = "swipe:empty"
    const val END_OF_FEED = "swipe:end"
    const val FAILURE = "swipe:failure"
    const val RETRY = "swipe:retry"
    const val ILLUSTRATION = "swipe:illustration"
    const val NO_LINK = "swipe:no-link"
    const val OPEN = "swipe:open"
    const val OFFLINE_NOTICE = "swipe:offline-notice"
    const val OFFLINE_NOTICE_DISMISS = "swipe:offline-notice-dismiss"

    fun page(id: Long) = "swipe:page:$id"
}
