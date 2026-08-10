package fr.vbrosseau.freshrssdiscover.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError

/**
 * Maps a failure cause to a displayable message.
 *
 * The `when` is exhaustive over sealed types: adding a cause without a
 * message fails to compile. This prevents SPECS.md §3.3 from silently
 * degrading into a generic "login failed" message.
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
     * The technical message is not displayed: it is neither translated nor
     * understandable. It lives in the logs, where it serves diagnostics.
     */
    is AuthError.Unexpected -> stringResource(R.string.login_error_unexpected)
}

/** True when the failure concerns the address field rather than the form. */
internal val LoginFailure.concernsAddressField: Boolean
    get() = this is LoginFailure.Address
