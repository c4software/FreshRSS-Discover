package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.BuildConfig
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTime
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.presentation.UiStateSharing
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PERCENT = 100f

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
    /**
     * The setting does not just get stored: it schedules or cancels.
     *
     * Without this dependency, turning the reminder off would leave the next
     * day's work pending, and it would fire anyway.
     */
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    /**
     * Separate from the session: the confirmation is UI state, the session
     * comes from disk. Combining them in one `MutableStateFlow` would require
     * rewriting the session on each dialog opening, hence duplicating it.
     */
    private val signOutConfirmation = MutableStateFlow(false)

    /**
     * Result of the last manual purge, `null` until one has happened.
     *
     * It cannot come from the repository, which publishes what the cache
     * contains, not what a gesture just removed. That difference is exactly
     * what must be shown: with no upfront confirmation, the report is the
     * only feedback the user gets for the press.
     */
    private val lastPurgedCount = MutableStateFlow<Int?>(null)

    /**
     * Displayed thresholds come from the repository, never from a local copy.
     *
     * This is the core of GOAL-011-T04: the value shown is the one the read
     * detector will reread. A change is not applied to UI state and then
     * "also" stored; it is stored, and the display follows because it
     * observes the same source. The two cannot diverge, even if a write
     * fails.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.observeSession(),
        settingsRepository.observeReadingSettings(),
        // Grouped because `combine` only accepts up to five flows and they
        // share the same status: two disk-read preferences, unrelated to each
        // other.
        combine(
            settingsRepository.observeFeedPresentation(),
            settingsRepository.observeReminderEnabled(),
            settingsRepository.observeReminderTime(),
            ::StoredPreferences,
        ),
        cacheRepository.observeCacheStatus(),
        // The two purely local states are grouped before entering the main
        // `combine`: `combine` only accepts up to five flows, and grouping
        // them here also marks which ones do not come from disk.
        combine(signOutConfirmation, lastPurgedCount, ::TransientState),
    ) { session, settings, preferences, cache, transient ->
        stateOf(session, settings, preferences, cache, transient)
    }
        .stateIn(
            scope = viewModelScope,
            started = UiStateSharing,
            initialValue = stateOf(
                session = null,
                settings = ReadingSettings.Default,
                preferences = StoredPreferences(
                    presentation = FeedPresentation.Default,
                    // The reminder defaults to enabled in the repository:
                    // showing "off" during the first read would flicker the
                    // switch on every screen opening.
                    reminderEnabled = true,
                    reminderTime = ReminderTime.Automatic,
                ),
                cache = CacheStatus.Empty,
                transient = TransientState(confirming = false, purged = null),
            ),
        )

    /**
     * Enables or disables visibility-based marking (SPECS.md §4.5, §6).
     *
     * Nothing to do beyond storing, unlike the reading reminder: both feed
     * ViewModels already observe the settings and rebuild their detector on
     * each emission, so the change applies without restart and without this
     * screen touching the feed.
     *
     * The two thresholds are not rewritten: they stay as-is for re-enabling.
     */
    fun setAutoMarkAsReadEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoMarkAsReadEnabled(enabled) }
    }

    /** @param percent a slider position, so already within the domain bounds. */
    fun setVisibleFractionPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setVisibleFraction(percent / PERCENT) }
    }

    /**
     * The slider works in the stored unit, so nothing is converted.
     *
     * It used to hand over seconds, and the multiplication lived here. The
     * range now starts at 150 ms, which no whole number of seconds can
     * express: the unit had to become the domain's own, and the conversion
     * disappeared with it.
     */
    fun setContinuousVisibilityMillis(millis: Int) {
        viewModelScope.launch { settingsRepository.setContinuousVisibilityMillis(millis.toLong()) }
    }

    /**
     * Stores the presentation mode without copying it into UI state.
     *
     * The selected segment comes from the repository: if the write failed,
     * the screen would keep showing the mode actually applied rather than a
     * choice that did not take. This is also what upholds SPECS.md §4.8: the
     * change applies without restart because the feed screen observes the
     * same source.
     */
    fun setFeedPresentation(presentation: FeedPresentation) {
        viewModelScope.launch { settingsRepository.setFeedPresentation(presentation) }
    }

    /**
     * Stores the choice, then schedules or cancels the reminder.
     *
     * In that order and in the same coroutine: the repository is the
     * authority on what the user wants, the scheduler only draws the
     * consequences. Scheduling first would, if the write failed, leave an
     * armed reminder no setting claims to want.
     *
     * The displayed state is not touched here: it comes from the repository,
     * like the presentation mode. A switch flipping on its own would show a
     * choice the store may not have kept.
     */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            if (enabled) reminderScheduler.scheduleNext() else reminderScheduler.cancel()
        }
    }

    /**
     * Stores the hour choice, then reschedules at the new target.
     *
     * Same order and same coroutine as [setReminderEnabled], for the same
     * reason. Rescheduling only while the reminder is on: with it off there
     * is nothing pending to move, and arming a work here would undo the
     * cancellation the off switch just performed.
     */
    fun setReminderTime(time: ReminderTime) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(time)
            if (settingsRepository.observeReminderEnabled().first()) reminderScheduler.scheduleNext()
        }
    }

    /**
     * Requests confirmation instead of signing out.
     *
     * Required by SPECS.md §3.5: the action erases the token and the cache
     * and cannot be undone by going back.
     */
    fun requestSignOut() {
        signOutConfirmation.value = true
    }

    fun dismissSignOut() {
        signOutConfirmation.value = false
    }

    /**
     * The confirmation is closed before the call, not after.
     *
     * `signOut()` drops the session, which returns the user to the login
     * screen: waiting for it to complete would leave the dialog visible
     * during the transition.
     */
    fun confirmSignOut() {
        signOutConfirmation.value = false
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * Purges the cache without asking.
     *
     * What the gesture destroys (articles read and already known to the
     * server as read, SPECS.md §5.4) does not justify the confirmation
     * sign-out requires, which takes the token, unread articles, and pending
     * markings. The report serves as feedback: see [lastPurgedCount].
     */
    fun purgeCache() {
        viewModelScope.launch { lastPurgedCount.value = cacheRepository.purgeReadArticles() }
    }

    private fun stateOf(
        session: AuthSession?,
        settings: ReadingSettings,
        preferences: StoredPreferences,
        cache: CacheStatus,
        transient: TransientState,
    ): SettingsUiState = SettingsUiState(
        account = session?.let { SettingsAccount(serverAddress = it.server.baseUrl, username = it.username) },
        presentation = preferences.presentation,
        isReminderEnabled = preferences.reminderEnabled,
        reminderHour = reminderHourOf(preferences.reminderTime),
        isAutoMarkAsReadEnabled = settings.autoMarkAsReadEnabled,
        visibleFraction = visibleFractionThresholdOf(settings),
        continuousVisibility = continuousVisibilityThresholdOf(settings),
        cache = SettingsCache(
            articleCount = cache.articleCount,
            purgeableCount = cache.purgeableCount,
            lastPurgedCount = transient.purged,
        ),
        appVersion = BuildConfig.VERSION_NAME,
        isSignOutConfirmationVisible = transient.confirming,
    )

    /**
     * State the screen carries on its own; none of it is on disk.
     *
     * Grouping is not only a convenience for `combine`'s five-flow limit: it
     * marks the boundary between what survives the screen's closing and what
     * disappears with it.
     */
    private data class TransientState(
        val confirming: Boolean,
        val purged: Int?,
    )

    /**
     * Persisted preferences that are not thresholds.
     *
     * A convenience grouping for `combine`'s five-flow limit. The two
     * settings are unrelated, which is exactly why they stay two fields and
     * not a domain type.
     */
    private data class StoredPreferences(
        val presentation: FeedPresentation,
        val reminderEnabled: Boolean,
        val reminderTime: ReminderTime,
    )
}
