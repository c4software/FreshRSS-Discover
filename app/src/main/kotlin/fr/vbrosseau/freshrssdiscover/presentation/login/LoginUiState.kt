package fr.vbrosseau.freshrssdiscover.presentation.login

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError

/**
 * Ce que l'écran de connexion affiche, entièrement dérivé par le ViewModel.
 *
 * Aucun champ n'est calculable depuis un Composable : `canSubmit` et
 * `showsInsecureWarning` résultent d'une analyse de la saisie, qui n'a rien à
 * faire dans une fonction de rendu (AGENTS.md §9).
 */
data class LoginUiState(
    val serverAddress: String = "",
    val username: String = "",
    val apiPassword: String = "",
    val isSubmitting: Boolean = false,
    val failure: LoginFailure? = null,
    /**
     * Vraie pour une adresse valide mais servie en clair.
     *
     * Avertir sans bloquer : les instances auto-hébergées sur réseau local sont
     * un cas réel (SPECS.md §3.1).
     */
    val showsInsecureWarning: Boolean = false,
    val canSubmit: Boolean = false,
)

/**
 * Ce qui a empêché la connexion.
 *
 * Deux familles, parce que les moments diffèrent : l'adresse est rejetée avant
 * le moindre appel réseau, le reste après. Les confondre obligerait l'écran à
 * deviner s'il doit désigner le champ « adresse » ou l'ensemble du formulaire.
 */
sealed interface LoginFailure {
    /** La saisie de l'adresse est inexploitable. */
    sealed interface Address : LoginFailure {
        data object Blank : Address

        data object Malformed : Address

        data class UnsupportedScheme(val scheme: String) : Address
    }

    /** Le serveur a été contacté, et quelque chose a échoué. */
    data class Server(val error: AuthError) : LoginFailure
}
