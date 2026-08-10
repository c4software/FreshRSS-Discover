package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.vbrosseau.freshrssdiscover.R

/**
 * Puts into words an age computed elsewhere.
 *
 * The computation belongs to the ViewModel, the wording to resources: the
 * only way to satisfy both "no computation in a Composable" and "every
 * displayed string is a resource" (AGENTS.md §9).
 *
 * Units are abbreviated ("min", "h", "j") because the card displays them on
 * the same line as the feed name, where space is scarce. Years keep their
 * long form, which stays readable.
 */
@Composable
internal fun RelativeTime.label(): String = when (this) {
    RelativeTime.JustNow -> stringResource(R.string.discover_time_just_now)
    is RelativeTime.Minutes -> stringResource(R.string.discover_time_minutes, count)
    is RelativeTime.Hours -> stringResource(R.string.discover_time_hours, count)
    is RelativeTime.Days -> stringResource(R.string.discover_time_days, count)
    is RelativeTime.Months -> stringResource(R.string.discover_time_months, count)
    is RelativeTime.Years -> pluralStringResource(R.plurals.discover_time_years, count, count)
}

/**
 * One message per cause.
 *
 * The `when` is exhaustive: adding a cause without writing its message will
 * not compile, which prevents the screen from degrading into a generic
 * failure.
 */
@Composable
internal fun DiscoverFailure.message(): String = when (this) {
    DiscoverFailure.NoNetwork -> stringResource(R.string.discover_error_no_network)
    DiscoverFailure.ServerUnreachable -> stringResource(R.string.discover_error_server_unreachable)
    DiscoverFailure.Unexpected -> stringResource(R.string.discover_error_unexpected)
}
