package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.annotation.StringRes
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.reminder.MINUTES_PER_HOUR
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTime
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

/**
 * Rounding of the duration slider, in milliseconds.
 *
 * Unlike the fraction, this slider carries no tick marks: its range now spans
 * 150 ms to 5 s, and a graduation every 50 ms would draw ninety-six of them
 * across a phone's width — an unreadable comb, and a thumb that snaps to
 * positions three device-independent pixels apart. The slider is continuous
 * and the value is rounded here instead, which keeps the gesture comparative
 * ("mark earlier" or "later", SPECS.md §7.1) while leaving every useful value
 * reachable.
 *
 * 50 ms is a quarter of a sampling period: finer would promise a precision the
 * 5 Hz sampling cannot deliver.
 */
internal const val CONTINUOUS_VISIBILITY_MILLIS_ROUNDING = 50

private const val PERCENT = 100
private const val MILLIS_PER_SECOND = 1_000

/**
 * State displayed by the settings screen, fully derived by the ViewModel.
 *
 * Nothing here is computable from a Composable (AGENTS.md §9): thresholds are
 * already converted into their display unit (percent, milliseconds) because
 * going from `0.6f` and `200L` to "60%" and "200 ms" is a computation that
 * does not belong in a rendering function.
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
    /** Continuous display duration required by SPECS.md §4.5, in milliseconds. */
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
    /**
     * How the reminder hour is chosen (SPECS.md §4.9, §6): learned, or fixed
     * by the user. Hour and minute separated rather than a formatted string:
     * the screen hands them to the time-picker as well as to the label, and
     * the resource does the two-digit formatting.
     */
    val reminderHour: SettingsReminderHour = SettingsReminderHour.Automatic,
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
 * endpoints. Zero means a continuous slider, and then [rounding] is what
 * keeps the value on sensible increments.
 */
data class SettingsThreshold(
    val value: Int,
    val range: IntRange,
    val stepCount: Int,
    /**
     * Increment the slider's output is snapped to.
     *
     * 1 for a stepped slider, where the steps already place the thumb and
     * rounding to the nearest integer is enough. Greater than 1 only for a
     * continuous slider, which otherwise emits every intermediate value.
     */
    val rounding: Int = 1,
    /**
     * The value spelled out, unit already chosen.
     *
     * Part of the state and not computed by the screen: picking between "200
     * ms" and "1,2 s" is a conversion, and this file's own contract keeps
     * those out of rendering functions (AGENTS.md §9). The Composable is left
     * with resolving the resource, which only it can do.
     */
    val label: SettingsThresholdLabel,
) {
    /**
     * Turns a slider position into a value the repository accepts.
     *
     * `Slider` only works in `Float`, so the conversion has to happen
     * somewhere; doing it here rather than in the Composable keeps it
     * testable, and keeps the clamp next to the bounds it enforces. That clamp
     * is not decoration: rounding 4 990 up to 5 000 is fine, but rounding
     * 5 000 up would produce a value `ReadingSettings` refuses, and the
     * `require` would fire on a gesture the user is entitled to make.
     */
    fun snapped(rawValue: Float): Int =
        ((rawValue / rounding).roundToInt() * rounding).coerceIn(range.first, range.last)
}

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

/**
 * Reminder hour as the screen shows it (SPECS.md §4.9, §6).
 *
 * A UI-side mirror of the domain's `ReminderTime` rather than the domain type
 * itself: the domain carries a minute-of-day, and splitting it into hour and
 * minute for the picker is a conversion the ViewModel owns, not the screen.
 */
sealed interface SettingsReminderHour {
    /** The application learns from the reading histogram. */
    data object Automatic : SettingsReminderHour

    /** An hour the user chose. */
    data class Fixed(val hour: Int, val minute: Int) : SettingsReminderHour
}

/** Converts the stored choice into what the screen displays. */
fun reminderHourOf(time: ReminderTime): SettingsReminderHour = when (time) {
    ReminderTime.Automatic -> SettingsReminderHour.Automatic
    is ReminderTime.Fixed ->
        SettingsReminderHour.Fixed(
            hour = time.at.value / MINUTES_PER_HOUR,
            minute = time.at.value % MINUTES_PER_HOUR,
        )
}

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
    FeedPresentation.Immersive -> R.string.settings_presentation_immersive
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
    FeedPresentation.Immersive -> R.string.settings_presentation_immersive_description
}

/** Converts the domain fraction to a whole percentage, bounds included. */
fun visibleFractionThresholdOf(settings: ReadingSettings): SettingsThreshold {
    val lowest = percentOf(ReadingSettings.VisibleFractionRange.start)
    val highest = percentOf(ReadingSettings.VisibleFractionRange.endInclusive)
    val range = lowest..highest
    val percent = percentOf(settings.visibleFraction)
    return SettingsThreshold(
        value = percent,
        range = range,
        stepCount = stepCountOf(range, VISIBLE_FRACTION_PERCENT_STEP),
        label = SettingsThresholdLabel(R.string.settings_visible_fraction_value, percent),
    )
}

/**
 * Carries the domain duration in milliseconds, bounds included.
 *
 * Milliseconds and no longer seconds: the range starts at 150 ms, which whole
 * seconds could only render as "0 s". The unit is the stored one, so nothing
 * is converted here — only [continuousVisibilityLabelOf] turns it into
 * something readable.
 *
 * [SettingsThreshold.stepCount] is zero, i.e. a continuous slider; see
 * [CONTINUOUS_VISIBILITY_MILLIS_ROUNDING].
 */
fun continuousVisibilityThresholdOf(settings: ReadingSettings): SettingsThreshold {
    val bounds = ReadingSettings.ContinuousVisibilityRange
    val millis = settings.continuousVisibilityMillis.toInt()
    return SettingsThreshold(
        value = millis,
        range = bounds.first.toInt()..bounds.last.toInt(),
        stepCount = 0,
        rounding = CONTINUOUS_VISIBILITY_MILLIS_ROUNDING,
        label = continuousVisibilityLabelOf(millis),
    )
}

/**
 * Chooses the unit the duration is spelled out in.
 *
 * The range covers two orders of magnitude, and one unit cannot serve both
 * ends: "0,2 s" hides the very precision the low end exists for, "5000 ms"
 * asks the reader to count digits. Below a second the value is whole
 * milliseconds, above it a single decimal — enough to distinguish two
 * positions of the thumb, never enough to suggest more.
 *
 * The choice is made here and not in the Composable: mapping a value to a
 * resource is a conversion (AGENTS.md §2, §9), and it is verified without
 * running Compose.
 */
internal fun continuousVisibilityLabelOf(millis: Int): SettingsThresholdLabel =
    if (millis < MILLIS_PER_SECOND) {
        SettingsThresholdLabel(R.string.settings_continuous_visibility_value_millis, millis)
    } else {
        SettingsThresholdLabel(
            R.string.settings_continuous_visibility_value_seconds,
            millis.toFloat() / MILLIS_PER_SECOND,
        )
    }

/**
 * A value and the resource that spells it out, the unit being already chosen.
 *
 * [argument] is deliberately untyped: the two resources do not take the same
 * kind of number — one an `Int`, the other a `Float` formatted to one decimal
 * — and `stringResource` takes them both.
 */
data class SettingsThresholdLabel(
    @StringRes val resId: Int,
    val argument: Any,
)

private fun percentOf(fraction: Float): Int = (fraction * PERCENT).roundToInt()

/**
 * Number of intermediate steps for a slider covering [range] in [step] increments.
 *
 * Subtracting 1 distinguishes steps from positions: without it, the slider
 * would offer one more position than the range contains.
 */
private fun stepCountOf(range: IntRange, step: Int): Int = (range.last - range.first) / step - 1
