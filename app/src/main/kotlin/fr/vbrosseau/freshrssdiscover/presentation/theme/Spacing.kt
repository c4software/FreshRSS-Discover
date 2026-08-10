package fr.vbrosseau.freshrssdiscover.presentation.theme

import androidx.compose.ui.unit.dp

/** The application's single spacing scale. */
object Spacing {
    /**
     * The named absence of margin.
     *
     * For places where zero is a decision, not a default: the bottom of the
     * feed card, whose margin comes from the share button's touch target. A
     * literal `0.dp` there would read as an omission, and AGENTS.md §9
     * requires recurring dimensions to go through this scale rather than
     * scattered `.dp` values.
     */
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
