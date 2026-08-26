package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.subscription.FeedUrl
import fr.vbrosseau.freshrssdiscover.domain.subscription.FeedUrlResult
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionRepository
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lists, adds and removes the account's feeds (SPECS.md §6).
 *
 * The list is re-read from the server after every successful action rather
 * than patched locally: the server assigns the identifier and the title of
 * a new feed, and only it knows the order. Patching would show a row the
 * next opening contradicts.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState

    init {
        load()
    }

    /**
     * Re-reads the list; also what the retry after a failed listing does.
     *
     * The list already shown stays until the new one arrives: after an
     * addition the rows would otherwise vanish for the round trip, and the
     * spinner would read as something having gone wrong.
     */
    fun load() {
        _uiState.update { it.copy(loadFailure = null) }
        viewModelScope.launch {
            when (val outcome = repository.list()) {
                is Outcome.Success -> _uiState.update { it.copy(subscriptions = outcome.value) }
                is Outcome.Failure -> _uiState.update { it.copy(loadFailure = messageOf(outcome.error)) }
            }
        }
    }

    /** The notice goes as soon as the user types again: it described the previous attempt. */
    fun onDraftChange(url: String) {
        _uiState.update { it.copy(draftUrl = url, notice = null) }
    }

    /**
     * Validates before sending: the server's `400` says nothing about why,
     * so what can be told on the device is told here (`FeedUrl`).
     */
    fun onAdd() {
        when (val parsed = FeedUrl.parse(_uiState.value.draftUrl)) {
            FeedUrlResult.Blank -> _uiState.update { it.copy(notice = R.string.subscriptions_error_blank) }
            FeedUrlResult.Invalid -> _uiState.update { it.copy(notice = R.string.subscriptions_error_invalid) }
            is FeedUrlResult.Valid ->
                submit(R.string.subscriptions_added, clearsDraft = true) { repository.subscribe(parsed.url) }
        }
    }

    fun onRemoveRequest(subscription: Subscription) {
        _uiState.update { it.copy(removalCandidate = subscription) }
    }

    fun onRemoveDismiss() {
        _uiState.update { it.copy(removalCandidate = null) }
    }

    fun onRemoveConfirm() {
        val candidate = _uiState.value.removalCandidate ?: return
        _uiState.update { it.copy(removalCandidate = null) }
        submit(R.string.subscriptions_removed, clearsDraft = false) { repository.unsubscribe(candidate.id) }
    }

    /**
     * One path for both actions: hold the form, act, then reload on success
     * or report on failure. The draft is only cleared by a successful
     * addition: a refused address stays in the field to be corrected, and a
     * removal has nothing to do with what is being typed.
     */
    private fun submit(
        @StringRes successNotice: Int,
        clearsDraft: Boolean,
        action: suspend () -> SubscriptionResult<Unit>,
    ) {
        _uiState.update { it.copy(isSubmitting = true, notice = null) }
        viewModelScope.launch {
            when (val outcome = action()) {
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            draftUrl = if (clearsDraft) "" else it.draftUrl,
                            notice = successNotice,
                        )
                    }
                    load()
                }

                is Outcome.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, notice = messageOf(outcome.error)) }
            }
        }
    }
}
