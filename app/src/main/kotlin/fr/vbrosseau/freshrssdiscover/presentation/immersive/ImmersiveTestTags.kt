package fr.vbrosseau.freshrssdiscover.presentation.immersive

/** Test tags for the Immersive view. */
object ImmersiveTestTags {
    const val PAGER = "immersive:pager"
    const val EMPTY = "immersive:empty"
    const val END_OF_FEED = "immersive:end"
    const val FAILURE = "immersive:failure"
    const val RETRY = "immersive:retry"
    const val ILLUSTRATION = "immersive:illustration"
    const val NO_LINK = "immersive:no-link"
    const val OFFLINE_NOTICE = "immersive:offline-notice"
    const val OFFLINE_NOTICE_DISMISS = "immersive:offline-notice-dismiss"
    const val STALE_NOTICE = "immersive:stale-notice"
    const val STALE_NOTICE_REFRESH = "immersive:stale-notice-refresh"
    const val STALE_NOTICE_DISMISS = "immersive:stale-notice-dismiss"

    fun page(id: Long) = "immersive:page:$id"

    fun share(id: Long) = "immersive:share:$id"
}
