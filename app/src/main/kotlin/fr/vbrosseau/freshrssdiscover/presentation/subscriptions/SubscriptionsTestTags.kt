package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId

/** Test tags for the feeds screen. */
object SubscriptionsTestTags {
    const val URL_FIELD = "subscriptions:url"
    const val ADD = "subscriptions:add"
    const val NOTICE = "subscriptions:notice"
    const val LOADING = "subscriptions:loading"
    const val EMPTY = "subscriptions:empty"
    const val FAILURE = "subscriptions:failure"
    const val RETRY = "subscriptions:retry"
    const val REMOVE_DIALOG = "subscriptions:remove-dialog"
    const val REMOVE_CONFIRM = "subscriptions:remove-confirm"
    const val REMOVE_CANCEL = "subscriptions:remove-cancel"

    /** One tag per row, and one per remove icon, suffixed by the identifier. */
    fun rowOf(id: SubscriptionId): String = "subscriptions:row-${id.value}"

    fun removeOf(id: SubscriptionId): String = "subscriptions:remove-${id.value}"
}
