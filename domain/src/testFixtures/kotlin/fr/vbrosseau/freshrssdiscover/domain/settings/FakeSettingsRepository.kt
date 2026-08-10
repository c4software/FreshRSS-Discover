package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory settings for tests.
 *
 * Validates like the real repository: a permissive fake would accept an
 * out-of-bounds call the real implementation rejects, making the test pass on
 * a path impossible in production.
 */
class FakeSettingsRepository(
    initial: ReadingSettings = ReadingSettings.Default,
    initialPresentation: FeedPresentation = FeedPresentation.Default,
) : SettingsRepository {
    private val settings = MutableStateFlow(initial)
    private val presentation = MutableStateFlow(initialPresentation)

    /** Number of writes received, across all settings. */
    var writeCount: Int = 0
        private set

    /** Current value, to verify what was stored without collecting. */
    val current: ReadingSettings
        get() = settings.value

    /** Current mode, same purpose as [current]. */
    val currentPresentation: FeedPresentation
        get() = presentation.value

    /**
     * Enables or disables automatic marking without going through a write.
     *
     * A plain function rather than [setAutoMarkAsReadEnabled]: ViewModel
     * tests are not `runTest`, and they stage an initial state, not a user
     * gesture, hence no [writeCount] increment.
     */
    fun setAutomaticMarking(enabled: Boolean) {
        settings.value = settings.value.copy(autoMarkAsReadEnabled = enabled)
    }

    override fun observeReadingSettings(): StateFlow<ReadingSettings> = settings

    override fun observeFeedPresentation(): StateFlow<FeedPresentation> = presentation

    /** The reading reminder (SPECS.md §4.9), enabled by default as in production. */
    val reminderEnabled = MutableStateFlow(true)

    override fun observeReminderEnabled(): Flow<Boolean> = reminderEnabled

    override suspend fun setReminderEnabled(value: Boolean) {
        reminderEnabled.value = value
    }

    override suspend fun setFeedPresentation(value: FeedPresentation) {
        writeCount++
        presentation.value = value
    }

    override suspend fun setVisibleFraction(value: Float) {
        writeCount++
        settings.value = settings.value.copy(visibleFraction = value)
    }

    override suspend fun setContinuousVisibilityMillis(value: Long) {
        writeCount++
        settings.value = settings.value.copy(continuousVisibilityMillis = value)
    }

    override suspend fun setAutoMarkAsReadEnabled(value: Boolean) {
        writeCount++
        settings.value = settings.value.copy(autoMarkAsReadEnabled = value)
    }
}
