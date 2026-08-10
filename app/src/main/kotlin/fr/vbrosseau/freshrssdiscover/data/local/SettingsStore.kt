package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the auto-mark thresholds across launches (SPECS.md §6).
 *
 * Why this store exists: previously the settings screen showed constants
 * copied from `ReadDetector` into `SettingsViewModel`. Two separate
 * declarations with nothing linking them: changing the detector's default
 * left the screen announcing the old value, with no way for the user to
 * notice the displayed number was not the applied one. Here,
 * [ReadingSettings.Default] is the single origin of initial values, and that
 * same data is what the screen displays.
 *
 * Implements [SettingsRepository] directly instead of being wrapped in a
 * `DefaultSettingsRepository`: there is no remote source, no secret to
 * encrypt, no conversion — an extra layer would only relay (AGENTS.md §2, no
 * anticipation). The day a setting comes from the server, the abstraction
 * will arrive with its second use case.
 *
 * Also persists the feed presentation mode (SPECS.md §4.8): List or Swipe.
 * Same store, because it is the same kind of data — a preference the user
 * chooses and the application must restore at the next launch.
 *
 * Shares the application's `DataStore<Preferences>` with [SessionStore]: its
 * keys are prefixed `reading.` and `display.`, so a logout — which only wipes
 * `session.` keys — leaves the settings in place. Deliberate: reading
 * thresholds and presentation mode are usage preferences, not account data.
 */
@Singleton
internal class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    /**
     * `distinctUntilChanged` is not an optimization here. DataStore emits on
     * every write of the file, not the key: touching any preference — the
     * last server contact date, for instance, written on every received page —
     * would re-emit these unchanged settings. The feed ViewModels rebuild
     * their read detector on each emission, and would therefore reset the
     * running visibility timers (SPECS.md §4.5) in the middle of a reading.
     *
     * An unreadable or aberrant value is coerced back into bounds rather than
     * failing the read.
     *
     * The preferences file may have been written by a version predating the
     * current bounds, or restored from a backup. Throwing here would prevent
     * the application from starting over a secondary setting; the corrected
     * value is rewritten on the user's next action.
     */
    override fun observeReadingSettings(): Flow<ReadingSettings> =
        dataStore.data.map(::readSettings).distinctUntilChanged()

    override suspend fun setVisibleFraction(value: Float) {
        require(value in ReadingSettings.VisibleFractionRange) {
            "fraction visible hors bornes : $value"
        }
        dataStore.edit { it[Keys.VisibleFraction] = value }
    }

    override suspend fun setContinuousVisibilityMillis(value: Long) {
        require(value in ReadingSettings.ContinuousVisibilityRange) {
            "durée de visibilité continue hors bornes : $value"
        }
        dataStore.edit { it[Keys.ContinuousVisibilityMillis] = value }
    }

    override suspend fun setAutoMarkAsReadEnabled(value: Boolean) {
        dataStore.edit { it[Keys.AutoMarkAsReadEnabled] = value }
    }

    /**
     * The presentation mode, re-read on every file change.
     *
     * Same tolerance as the thresholds, for the same reason: an unknown mode
     * name — earlier version, restored backup, damaged file — falls back to
     * List rather than failing the read. Details in
     * `FeedPresentation.fromStoredName`.
     */
    override fun observeFeedPresentation(): Flow<FeedPresentation> =
        dataStore.data.map { FeedPresentation.fromStoredName(it[Keys.FeedPresentation]) }
            .distinctUntilChanged()

    override suspend fun setFeedPresentation(value: FeedPresentation) {
        dataStore.edit { it[Keys.FeedPresentation] = value.name }
    }

    /**
     * The reminder is active until the user says otherwise: a missing key
     * means `true`. The opposite would force the user to enable it after
     * granting the permission, that is, to say yes twice.
     */
    override fun observeReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[Keys.ReminderEnabled] ?: true }.distinctUntilChanged()

    override suspend fun setReminderEnabled(value: Boolean) {
        dataStore.edit { it[Keys.ReminderEnabled] = value }
    }

    private fun readSettings(preferences: Preferences): ReadingSettings = ReadingSettings.coerced(
        visibleFraction = preferences[Keys.VisibleFraction] ?: ReadingSettings.Default.visibleFraction,
        continuousVisibilityMillis = preferences[Keys.ContinuousVisibilityMillis]
            ?: ReadingSettings.Default.continuousVisibilityMillis,
        /*
         * A missing key means enabled: the case of every installation
         * predating the setting, and changing its behavior on update would
         * make marking appear broken.
         */
        autoMarkAsReadEnabled = preferences[Keys.AutoMarkAsReadEnabled]
            ?: ReadingSettings.Default.autoMarkAsReadEnabled,
    )

    /**
     * Keys, prefixed by what they belong to.
     *
     * Three families share the same file: `session.` (wiped on logout),
     * `reading.` (marking thresholds) and `display.` (what the user sees).
     * The presentation mode is not a `reading.` key: it says nothing about
     * what makes an article read, it says how the feed is browsed. Mixing
     * them would make it impossible to wipe one family without taking the
     * others.
     */
    private object Keys {
        val VisibleFraction = floatPreferencesKey("reading.visible_fraction")
        val ContinuousVisibilityMillis = longPreferencesKey("reading.continuous_visibility_millis")

        /**
         * `reading.` like the two thresholds: it says, as they do, what makes
         * an article read — and it is read in the same emission.
         */
        val AutoMarkAsReadEnabled = booleanPreferencesKey("reading.auto_mark_as_read")

        /**
         * A string, not an integer: see `FeedPresentation.fromStoredName`,
         * which explains why the `ordinal` would be a trap.
         */
        val FeedPresentation = stringPreferencesKey("display.feed_presentation")

        /**
         * Neither `reading.` nor `display.`: the reminder says nothing about
         * what makes an article read, and it acts precisely when nothing is
         * displayed.
         */
        val ReminderEnabled = booleanPreferencesKey("reminder.enabled")
    }
}
