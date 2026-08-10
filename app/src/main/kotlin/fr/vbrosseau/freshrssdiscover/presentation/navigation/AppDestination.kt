package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.vbrosseau.freshrssdiscover.R

/**
 * Destinations shown in the navigation bar.
 *
 * Grouping route, labels, and icon here keeps them from diverging: adding a
 * destination means adding an entry, and the bar follows.
 *
 * Two labels, not one: the bar only has room for a single line of text. The
 * screen title keeps the full label.
 */
enum class AppDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    DISCOVER(
        route = AppRoutes.DISCOVER,
        labelRes = R.string.destination_discover,
        shortLabelRes = R.string.destination_short_discover,
        iconRes = R.drawable.ic_nav_discover,
    ),
    SETTINGS(
        route = AppRoutes.SETTINGS,
        labelRes = R.string.destination_settings,
        shortLabelRes = R.string.destination_short_settings,
        iconRes = R.drawable.ic_nav_settings,
    ),
    ;

    companion object {
        fun forRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
