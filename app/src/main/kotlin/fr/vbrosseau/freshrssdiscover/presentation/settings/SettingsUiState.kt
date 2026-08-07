package fr.vbrosseau.freshrssdiscover.presentation.settings

/**
 * Ce que l'écran de réglages affiche, entièrement dérivé par le ViewModel.
 *
 * Rien n'y est calculable depuis un Composable (AGENTS.md §9) : les seuils sont
 * déjà convertis dans l'unité affichée — pourcentage et secondes — parce que
 * passer de `0.6f` et `1_000L` à « 60 % » et « 1 s » est un calcul, et qu'il
 * n'a pas sa place dans une fonction de rendu.
 */
data class SettingsUiState(
    /**
     * Session observée, `null` tant qu'elle n'est pas lue ou après déconnexion.
     *
     * L'écran distingue les deux affichages : sans compte, il n'y a ni adresse
     * ni identifiant à montrer, et la déconnexion n'a plus d'objet.
     */
    val account: SettingsAccount? = null,
    /** Part de hauteur affichée exigée par SPECS.md §4.5, en pourcentage entier. */
    val visibleFractionPercent: Int = 0,
    /** Durée d'affichage continu exigée par SPECS.md §4.5, en secondes. */
    val continuousVisibilitySeconds: Int = 0,
    /** Nom de version de l'application, tel que produit par la compilation. */
    val appVersion: String = "",
    /**
     * Vraie pendant que la confirmation de déconnexion est posée.
     *
     * Dans l'état publié et non dans l'écran : SPECS.md §3.5 fait de la
     * confirmation une étape du geste, et une `rememberSaveable` locale la
     * rendrait intestable depuis le ViewModel.
     */
    val isSignOutConfirmationVisible: Boolean = false,
)

/** Le compte connecté, en lecture seule (SPECS.md §6). */
data class SettingsAccount(
    val serverAddress: String,
    val username: String,
)
