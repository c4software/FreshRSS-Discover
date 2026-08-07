package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.vbrosseau.freshrssdiscover.R

/**
 * Met en mots une ancienneté calculée ailleurs.
 *
 * Le calcul appartient au ViewModel, la traduction aux ressources : c'est la
 * seule façon de respecter à la fois « aucun calcul dans un Composable » et
 * « toute chaîne affichée est une ressource » (AGENTS.md §9).
 *
 * Les unités sont abrégées — « min », « h », « j » — parce que la carte les
 * affiche sur la même ligne que le nom du flux, où la place manque. Les années
 * gardent leur forme longue : « il y a 2 a » ne se lit pas.
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
 * Un message par cause.
 *
 * Le `when` est exhaustif : ajouter une cause sans lui écrire de message ne
 * compilera pas, ce qui empêche l'écran de se dégrader en « échec » générique.
 */
@Composable
internal fun DiscoverFailure.message(): String = when (this) {
    DiscoverFailure.NoNetwork -> stringResource(R.string.discover_error_no_network)
    DiscoverFailure.ServerUnreachable -> stringResource(R.string.discover_error_server_unreachable)
    DiscoverFailure.Unexpected -> stringResource(R.string.discover_error_unexpected)
}
