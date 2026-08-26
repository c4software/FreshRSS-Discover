package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import androidx.annotation.StringRes
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionError

/**
 * State displayed by the feeds screen (SPECS.md §6), fully derived by the
 * ViewModel.
 *
 * The list and the form are separate concerns with separate failures: a
 * listing that could not be read leaves nothing to show, while a refused
 * addition leaves the list intact and the typed address in place — the user
 * fixes it and tries again.
 */
data class SubscriptionsUiState(
    /** The listing, or `null` until the server answered (or refused). */
    val subscriptions: List<Subscription>? = null,
    /** Why the listing is absent; `null` while loading or once loaded. */
    @field:StringRes val loadFailure: Int? = null,
    /** The address being typed; kept on failure, cleared on success. */
    val draftUrl: String = "",
    /**
     * Outcome of the last add or remove, `null` when there is nothing to
     * say. A success says so too: with the list reloading underneath, the
     * only visible trace of an addition would otherwise be a row appearing
     * somewhere in the server's order.
     */
    @field:StringRes val notice: Int? = null,
    /** True while an add or remove is in flight; the form and the icons are held. */
    val isSubmitting: Boolean = false,
    /** The subscription whose removal awaits confirmation, `null` otherwise. */
    val removalCandidate: Subscription? = null,
) {
    val isLoading: Boolean get() = subscriptions == null && loadFailure == null
}

/**
 * The message for a failure: a conversion, so kept out of the Composable
 * (AGENTS.md §9) and testable without Compose.
 *
 * [SubscriptionError.Rejected] is worded for the addition — the only
 * action where the user can fix the input. On a removal the server would
 * be refusing an identifier it just listed, which the reload that follows
 * settles better than any sentence.
 */
@StringRes
fun messageOf(error: SubscriptionError): Int = when (error) {
    SubscriptionError.NoNetwork -> R.string.subscriptions_error_no_network
    SubscriptionError.ServerUnreachable -> R.string.subscriptions_error_server_unreachable
    SubscriptionError.SessionExpired -> R.string.subscriptions_error_session_expired
    SubscriptionError.Rejected -> R.string.subscriptions_error_rejected
    is SubscriptionError.Unexpected -> R.string.subscriptions_error_unexpected
}
