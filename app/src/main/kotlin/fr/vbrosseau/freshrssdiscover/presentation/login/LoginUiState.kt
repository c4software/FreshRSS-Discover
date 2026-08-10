package fr.vbrosseau.freshrssdiscover.presentation.login

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError

/**
 * State displayed by the login screen, fully derived by the ViewModel.
 *
 * No field is computable from a Composable: `canSubmit` and
 * `showsInsecureWarning` come from analyzing the input, which does not belong
 * in a rendering function (AGENTS.md §9).
 */
data class LoginUiState(
    val serverAddress: String = "",
    val username: String = "",
    val apiPassword: String = "",
    val isSubmitting: Boolean = false,
    val failure: LoginFailure? = null,
    /**
     * True for a valid address served in cleartext.
     *
     * Warn without blocking: self-hosted instances on a local network are a
     * real case (SPECS.md §3.1).
     */
    val showsInsecureWarning: Boolean = false,
    val canSubmit: Boolean = false,
)

/**
 * What prevented the login.
 *
 * Two families, because the timing differs: the address is rejected before
 * any network call, the rest after. Merging them would force the screen to
 * guess whether to point at the address field or at the whole form.
 */
sealed interface LoginFailure {
    /** The address input is unusable. */
    sealed interface Address : LoginFailure {
        data object Blank : Address

        data object Malformed : Address

        data class UnsupportedScheme(val scheme: String) : Address
    }

    /** The server was contacted and something failed. */
    data class Server(val error: AuthError) : LoginFailure
}
