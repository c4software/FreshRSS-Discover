package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.annotation.StringRes
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import kotlin.math.roundToInt

/**
 * Step of the visible-fraction slider, in percentage points.
 *
 * The steps are not an implementation convenience: nobody distinguishes 62%
 * from 65% in use, and a continuous slider would promise precision that does
 * not exist while making the intended value hard to hit with a thumb
 * (SPECS.md §7.1). Five positions are easy to target.
 */
private const val VISIBLE_FRACTION_PERCENT_STEP = 20

/** Step of the duration slider, in seconds; the smallest perceptible difference here. */
private const val CONTINUOUS_VISIBILITY_SECONDS_STEP = 1

private const val PERCENT = 100
private const val MILLIS_PER_SECOND = 1_000L

/**
 * State displayed by the settings screen, fully derived by the ViewModel.
 *
 * Nothing here is computable from a Composable (AGENTS.md §9): thresholds are
 * already converted into their display unit (percent, seconds) because going
 * from `0.6f` and `1_000L` to "60%" and "1 s" is a computation that does not
 * belong in a rendering function.
 */
data class SettingsUiState(
    /**
     * Observed session, `null` until read or after sign-out.
     *
     * The screen distinguishes the two displays: without an account there is
     * no address or username to show, and sign-out has no purpose.
     */
    val account: SettingsAccount? = null,
    /**
     * Feed presentation mode (SPECS.md §4.8), as stored.
     *
     * The domain type, not a UI-side copy: the two modes are persisted data,
     * not a display variant, and redeclaring the enum here would require
     * two-way translation of a two-value choice.
     */
    val presentation: FeedPresentation = FeedPresentation.Default,
    /**
     * Whether visibility-based marking happens (SPECS.md §4.5, §6).
     *
     * Drives the enabled state of the two sliders, which stay visible but
     * dimmed when off: hiding them would remove two settings without saying
     * why, leaving them active would offer to adjust something no longer
     * applied.
     *
     * The default follows the repository's (enabled) so the screen does not
     * briefly show dimmed sliders during the first disk read.
     */
    val isAutoMarkAsReadEnabled: Boolean = true,
    /** Displayed-height fraction required by SPECS.md §4.5, as a whole percentage. */
    val visibleFraction: SettingsThreshold = visibleFractionThresholdOf(ReadingSettings.Default),
    /** Continuous display duration required by SPECS.md §4.5, in seconds. */
    val continuousVisibility: SettingsThreshold = continuousVisibilityThresholdOf(ReadingSettings.Default),
    /**
     * Whether the daily reading reminder is wanted (SPECS.md §4.9, §6).
     *
     * A boolean, not the system permission state: below Android 13 there is
     * no notification permission to revoke, and a reminder that could not be
     * turned off would be a defect. The default follows the repository's
     * (enabled) so the screen does not briefly show an off switch during the
     * first disk read.
     */
    val isReminderEnabled: Boolean = true,
    /** Local cache contents and outcome of the last purge (SPECS.md §5.4, §6). */
    val cache: SettingsCache = SettingsCache(),
    /** Application version name, as produced by the build. */
    val appVersion: String = "",
    /**
     * True while the sign-out confirmation is shown.
     *
     * In the published state, not the screen: SPECS.md §3.5 makes the
     * confirmation a step of the gesture, and a local `rememberSaveable`
     * would make it untestable from the ViewModel.
     */
    val isSignOutConfirmationVisible: Boolean = false,
)

/**
 * An adjustable threshold, already expressed in its display unit.
 *
 * The bounds travel with the value instead of being hard-coded in the screen:
 * they come from `ReadingSettings`, and copying them UI-side would let a
 * slider offer a value the repository refuses to store.
 *
 * [stepCount] is the number of intermediate steps in Material 3 `Slider`
 * terms: five positions make four intervals, so three steps between the
 * endpoints.
 */
data class SettingsThreshold(
    val value: Int,
    val range: IntRange,
    val stepCount: Int,
)

/**
 * State displayed by the local cache section.
 *
 * [lastPurgedCount] replaces the confirmation the manual purge does not ask
 * for. The reasoning follows SPECS.md §5.4: the purge only removes articles
 * that are read and already known to the server as read, never an unread
 * article or a pending marking. It destroys nothing that is not both consumed
 * and re-downloadable, so no upfront promise is needed. Sign-out, by
 * contrast, requires one (SPECS.md §3.5) because it erases the token, the
 * whole cache including unread articles, and untransmitted markings.
 * Confirming both would level the difference and train the user to dismiss
 * the dialog that matters. An after-the-fact report informs better than a
 * question asked before.
 */
data class SettingsCache(
    /** Articles kept, read and unread alike. */
    val articleCount: Int = 0,
    /** What a purge would remove now: read and synchronized. */
    val purgeableCount: Int = 0,
    /** Articles removed by the last purge, `null` until one has happened. */
    val lastPurgedCount: Int? = null,
)

/** The signed-in account, read-only (SPECS.md §6). */
data class SettingsAccount(
    val serverAddress: String,
    val username: String,
)

/**
 * Short label of a mode, carried by the segment.
 *
 * A function rather than a `when` in the Composable: mapping a domain value
 * to a resource is a conversion, which AGENTS.md §2 keeps out of rendering
 * functions. It is testable here without running Compose.
 */
@StringRes
fun feedPresentationLabelOf(presentation: FeedPresentation): Int = when (presentation) {
    FeedPresentation.List -> R.string.settings_presentation_list
    FeedPresentation.Swipe -> R.string.settings_presentation_swipe
}

/**
 * Sentence describing the selected mode.
 *
 * Two words do not say what is gained or lost by switching: the description
 * answers the only question the control raises, namely what the feed will
 * look like after touching it.
 */
@StringRes
fun feedPresentationDescriptionOf(presentation: FeedPresentation): Int = when (presentation) {
    FeedPresentation.List -> R.string.settings_presentation_list_description
    FeedPresentation.Swipe -> R.string.settings_presentation_swipe_description
}

/** Converts the domain fraction to a whole percentage, bounds included. */
fun visibleFractionThresholdOf(settings: ReadingSettings): SettingsThreshold {
    val lowest = percentOf(ReadingSettings.VisibleFractionRange.start)
    val highest = percentOf(ReadingSettings.VisibleFractionRange.endInclusive)
    val range = lowest..highest
    return SettingsThreshold(
        value = percentOf(settings.visibleFraction),
        range = range,
        stepCount = stepCountOf(range, VISIBLE_FRACTION_PERCENT_STEP),
    )
}

/** Converts the domain duration to whole seconds, bounds included. */
fun continuousVisibilityThresholdOf(settings: ReadingSettings): SettingsThreshold {
    val lowest = secondsOf(ReadingSettings.ContinuousVisibilityRange.first)
    val highest = secondsOf(ReadingSettings.ContinuousVisibilityRange.last)
    val range = lowest..highest
    return SettingsThreshold(
        value = secondsOf(settings.continuousVisibilityMillis),
        range = range,
        stepCount = stepCountOf(range, CONTINUOUS_VISIBILITY_SECONDS_STEP),
    )
}

private fun percentOf(fraction: Float): Int = (fraction * PERCENT).roundToInt()

private fun secondsOf(millis: Long): Int = (millis / MILLIS_PER_SECOND).toInt()

/**
 * Number of intermediate steps for a slider covering [range] in [step] increments.
 *
 * Subtracting 1 distinguishes steps from positions: without it, the slider
 * would offer one more position than the range contains.
 */
private fun stepCountOf(range: IntRange, step: Int): Int = (range.last - range.first) / step - 1
