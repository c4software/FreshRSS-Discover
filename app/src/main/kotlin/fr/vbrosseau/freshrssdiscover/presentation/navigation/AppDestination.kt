package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.vbrosseau.freshrssdiscover.R

/**
 * Destinations visibles dans la barre de navigation.
 *
 * Rassembler route, libellés et icône ici évite qu'ils divergent : ajouter une
 * destination consiste à ajouter une entrée, et la barre suit.
 *
 * Deux libellés, et non un seul : la barre n'a de place que pour une ligne de
 * texte. Le titre de l'écran garde en revanche le libellé complet.
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
