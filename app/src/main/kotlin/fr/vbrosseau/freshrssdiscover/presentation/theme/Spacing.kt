package fr.vbrosseau.freshrssdiscover.presentation.theme

import androidx.compose.ui.unit.dp

/** Échelle d'espacement unique de l'application. */
object Spacing {
    /**
     * L'absence de marge, nommée.
     *
     * Elle existe pour les endroits où « zéro » est une **décision** et non un
     * défaut : le bas de la carte du flux, dont la marge est fournie par la
     * cible tactile du bouton de partage. Un `0.dp` écrit là se lirait comme un
     * oubli, et AGENTS.md §9 veut que les dimensions récurrentes passent par
     * cette échelle plutôt que par des `.dp` épars.
     */
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
