package fr.vbrosseau.freshrssdiscover.presentation.swipe

/** Test tags for the Swipe view. */
object SwipeTestTags {
    const val PAGER = "swipe:pager"
    const val EMPTY = "swipe:empty"
    const val END_OF_FEED = "swipe:end"
    const val FAILURE = "swipe:failure"
    const val RETRY = "swipe:retry"
    const val ILLUSTRATION = "swipe:illustration"
    const val NO_LINK = "swipe:no-link"
    const val OFFLINE_NOTICE = "swipe:offline-notice"
    const val OFFLINE_NOTICE_DISMISS = "swipe:offline-notice-dismiss"
    const val STALE_NOTICE = "swipe:stale-notice"
    const val STALE_NOTICE_REFRESH = "swipe:stale-notice-refresh"
    const val STALE_NOTICE_DISMISS = "swipe:stale-notice-dismiss"

    fun page(id: Long) = "swipe:page:$id"

    fun share(id: Long) = "swipe:share:$id"
}
