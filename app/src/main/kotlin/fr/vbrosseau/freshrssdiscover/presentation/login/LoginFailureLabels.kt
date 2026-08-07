package fr.vbrosseau.freshrssdiscover.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError

/**
 * Traduit une cause d'échec en message affichable.
 *
 * Le `when` est exhaustif sur des types scellés : ajouter une cause sans lui
 * écrire de message ne compilera pas. C'est ce qui empêche SPECS.md §3.3 de se
 * dégrader silencieusement en « échec de connexion » générique.
 */
@Composable
internal fun LoginFailure.message(): String = when (this) {
    LoginFailure.Address.Blank -> stringResource(R.string.login_error_address_blank)
    LoginFailure.Address.Malformed -> stringResource(R.string.login_error_address_malformed)
    is LoginFailure.Address.UnsupportedScheme -> stringResource(R.string.login_error_address_scheme, scheme)
    is LoginFailure.Server -> error.message()
}

@Composable
private fun AuthError.message(): String = when (this) {
    AuthError.NoNetwork -> stringResource(R.string.login_error_no_network)
    AuthError.ServerUnreachable -> stringResource(R.string.login_error_server_unreachable)
    AuthError.NotAFreshRssServer -> stringResource(R.string.login_error_not_freshrss)
    AuthError.ApiDisabled -> stringResource(R.string.login_error_api_disabled)
    AuthError.InvalidCredentials -> stringResource(R.string.login_error_invalid_credentials)
    AuthError.AuthorizationHeaderNotForwarded -> stringResource(R.string.login_error_header_not_forwarded)

    /*
     * Le message technique n'est **pas** affiché : il n'est ni traduit ni
     * compréhensible. Il vit dans les journaux, où il sert au diagnostic.
     */
    is AuthError.Unexpected -> stringResource(R.string.login_error_unexpected)
}

/** Vrai lorsque l'échec porte sur le champ « adresse » plutôt que sur le formulaire. */
internal val LoginFailure.concernsAddressField: Boolean
    get() = this is LoginFailure.Address
