package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshness
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Period between staleness re-checks.
 *
 * Something has to wake the rule: the six-hour threshold is crossed without
 * any event occurring; the app can stay open, screen off, without a single
 * page being requested. Without this wake-up, the notice would only appear
 * at the next gesture, that is, never for someone reopening the app and
 * waiting to see something.
 *
 * A five-minute lag is invisible against a six-hour threshold, and the cost
 * is negligible: the screen already samples visibility every 200 ms.
 */
private const val STALE_CHECK_PERIOD_MILLIS = 5L * 60L * 1_000L

/**
 * Says, at any moment, whether the displayed feed warrants an invitation to
 * refresh (SPECS.md §4.6).
 *
 * A shared class rather than the same code twice: both presentation modes
 * (SPECS.md §4.8) each have their ViewModel and the rule is the same; two
 * copies would diverge at the first fix. It also keeps the acknowledgement
 * consistent across modes: the repository is unique, and this class only
 * observes it.
 *
 * [isStale] does not say the strip is on screen: each screen's state decides
 * whether to show it, adding what only it knows: that it is offline, already
 * refreshing, or has nothing to display.
 */
internal class FeedStalenessWatcher(
    private val repository: FeedFreshnessRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _isStale = MutableStateFlow(false)
    val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

    /**
     * Wait in progress for the next deadline.
     *
     * Only one at a time: every freshness change (server contact,
     * acknowledgement) invalidates the previous one.
     */
    private var aging: Job? = null

    init {
        repository.observeFreshness()
            .onEach(::reconsider)
            .launchIn(scope)
    }

    /** The user silences the notice for the current timestamp. */
    fun acknowledge() {
        _isStale.value = false
        scope.launch { repository.acknowledgeStale() }
    }

    private fun reconsider(freshness: FeedFreshness) {
        aging?.cancel()
        _isStale.value = freshness.showsStaleNotice(clock.nowEpochMillis())

        /*
         * Wait for nothing when time can no longer change anything: without
         * a recorded server contact nothing ages, and an already-acknowledged
         * notice will only reopen at the next contact, which will produce an
         * emission and come back through here.
         */
        if (_isStale.value || !freshness.canGrowStale()) return

        aging = scope.launch {
            /*
             * Polling rather than a computed wait: a device clock that jumps
             * (time zone, manual adjustment, network sync) would make the
             * computed delay wrong, and the notice would then wait for a
             * deadline that never arrives.
             */
            while (!freshness.showsStaleNotice(clock.nowEpochMillis())) {
                delay(STALE_CHECK_PERIOD_MILLIS)
            }
            _isStale.value = true
        }
    }
}

/** Whether the mere passage of time can still change anything. */
private fun FeedFreshness.canGrowStale(): Boolean =
    lastRefreshEpochMillis != null && acknowledgedRefreshEpochMillis != lastRefreshEpochMillis
