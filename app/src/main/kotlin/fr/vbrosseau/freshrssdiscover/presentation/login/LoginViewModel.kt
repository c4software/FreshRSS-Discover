package fr.vbrosseau.freshrssdiscover.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        /*
         * Prefill after a rejected token: the user probably only has an API
         * password to renew, so retyping the server address would be needless
         * (SPECS.md §3.4).
         *
         * `first()` rather than a continuous collection: in-progress input
         * must never be overwritten by a repository write.
         */
        viewModelScope.launch {
            val hint = authRepository.observeLastSignInHint().first() ?: return@launch
            update { state ->
                if (state.serverAddress.isEmpty() && state.username.isEmpty()) {
                    state.copy(serverAddress = hint.server.baseUrl, username = hint.username)
                } else {
                    state
                }
            }
        }
    }

    fun onServerAddressChange(value: String) = update { it.copy(serverAddress = value) }

    fun onUsernameChange(value: String) = update { it.copy(username = value) }

    fun onApiPasswordChange(value: String) = update { it.copy(apiPassword = value) }

    /**
     * Attempts the login.
     *
     * The address is parsed first: rejecting it here avoids a network round
     * trip and, above all, allows pointing at the faulty field instead of
     * showing a general error.
     */
    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        when (val parsed = ServerAddress.parse(current.serverAddress)) {
            ServerAddressResult.Blank -> fail(LoginFailure.Address.Blank)
            ServerAddressResult.Malformed -> fail(LoginFailure.Address.Malformed)
            is ServerAddressResult.UnsupportedScheme -> fail(LoginFailure.Address.UnsupportedScheme(parsed.scheme))
            is ServerAddressResult.Valid -> signIn(parsed.address, current)
        }
    }

    private fun signIn(address: ServerAddress, state: LoginUiState) {
        _uiState.update { it.copy(isSubmitting = true, failure = null, canSubmit = false) }

        viewModelScope.launch {
            val credentials = Credentials(
                username = state.username.trim(),
                apiPassword = state.apiPassword,
            )

            when (val result = authRepository.signIn(address, credentials)) {
                is Outcome.Success ->
                    /*
                     * The password is cleared from the state once used: a
                     * `UiState` outlives the screen displaying it and would
                     * end up in a debug snapshot or a process restoration.
                     */
                    update { it.copy(isSubmitting = false, apiPassword = "") }

                is Outcome.Failure ->
                    update { it.copy(isSubmitting = false, failure = LoginFailure.Server(result.error)) }
            }
        }
    }

    private fun fail(failure: LoginFailure) = update { it.copy(failure = failure) }

    /**
     * Applies a change, then reevaluates the derived fields.
     *
     * Recomputing them here rather than at each call site guarantees they
     * cannot drift from the input: a `copy` that forgot them would leave the
     * button enabled on an empty form.
     */
    private fun update(transform: (LoginUiState) -> LoginUiState) {
        _uiState.update { previous -> derive(transform(previous)) }
    }

    private fun derive(state: LoginUiState): LoginUiState {
        val parsed = ServerAddress.parse(state.serverAddress)
        return state.copy(
            showsInsecureWarning = parsed is ServerAddressResult.Valid && !parsed.address.isSecure,
            canSubmit = !state.isSubmitting &&
                state.serverAddress.isNotBlank() &&
                state.username.isNotBlank() &&
                state.apiPassword.isNotEmpty(),
        )
    }
}
