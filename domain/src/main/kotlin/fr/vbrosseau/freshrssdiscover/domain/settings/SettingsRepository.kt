package fr.vbrosseau.freshrssdiscover.domain.settings

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTime
import kotlinx.coroutines.flow.Flow

/**
 * Access to the persisted automatic-marking settings (SPECS.md §4.5, §6).
 *
 * Observation is a [Flow], not a one-shot read: two readers coexist. The
 * settings screen must display the current value, and the read detector must
 * apply a new one without restart. A one-shot read would force the latter to
 * re-read at the right moment, i.e. guess when the user changed their mind.
 *
 * One writer per threshold rather than a `save(ReadingSettings)`: the screen
 * never modifies both at once, and rewriting the other value in passing would
 * overwrite a concurrent modification with a possibly stale copy.
 */
interface SettingsRepository {
    fun observeReadingSettings(): Flow<ReadingSettings>

    /**
     * @throws IllegalArgumentException if [value] is outside
     *   [ReadingSettings.VisibleFractionRange]. An out-of-bounds call comes
     *   from code, not the user, and silencing it would freeze the defect on
     *   disk.
     */
    suspend fun setVisibleFraction(value: Float)

    /** @throws IllegalArgumentException if [value] is outside [ReadingSettings.ContinuousVisibilityRange]. */
    suspend fun setContinuousVisibilityMillis(value: Long)

    /**
     * Turns visibility-based marking on or off (SPECS.md §4.5, §6).
     *
     * A separate writer rather than a `save(ReadingSettings)`, for the reason
     * stated above: the screen never touches two settings at once.
     *
     * No bounds to check, and no effect on the two thresholds: they stay
     * stored while marking is off, since they become applicable again when it
     * is turned back on.
     */
    suspend fun setAutoMarkAsReadEnabled(value: Boolean)

    /**
     * The feed presentation mode (SPECS.md §4.8, §6).
     *
     * A [Flow] distinct from [observeReadingSettings]: the two settings share
     * neither readers nor cadence. The thresholds only concern the read
     * detector; the mode decides which screen is shown, and folding it into
     * `ReadingSettings` would recompose the whole feed on any move of a
     * marking slider.
     *
     * As for the thresholds, observation beats a one-shot read: SPECS.md §4.8
     * requires the mode to apply without restart, so the feed screen must
     * learn of the change by itself.
     */
    fun observeFeedPresentation(): Flow<FeedPresentation>

    /**
     * Stores the chosen mode.
     *
     * No bounds to check, unlike the thresholds: the enum makes an invalid
     * value impossible to construct.
     */
    suspend fun setFeedPresentation(value: FeedPresentation)

    /**
     * Whether the daily reading reminder is wanted (SPECS.md §4.9, §6).
     *
     * A setting of its own, not a deduction from the system permission: below
     * Android 13 there is no notification permission to revoke, and a
     * reminder that could not be turned off would be a defect. Above, both
     * coexist: the permission says what the system allows, this setting what
     * the user wants.
     *
     * Enabled by default: a user who granted the permission already said yes
     * once, and asking again in the settings would make the feature look
     * inoperative.
     */
    fun observeReminderEnabled(): Flow<Boolean>

    suspend fun setReminderEnabled(value: Boolean)

    /**
     * How the reminder hour is chosen (SPECS.md §4.9, §6): learned from the
     * reading histogram, or fixed by the user.
     *
     * Automatic by default: the fixed hour exists for the person the learned
     * one bothers, not as a question everyone must answer first.
     */
    fun observeReminderTime(): Flow<ReminderTime>

    suspend fun setReminderTime(value: ReminderTime)
}
