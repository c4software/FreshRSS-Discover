package fr.vbrosseau.freshrssdiscover.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthResult
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
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
         * Préremplissage après un jeton refusé : l'utilisateur n'a
         * probablement qu'un mot de passe API à renouveler, lui faire retaper
         * l'adresse de son serveur serait gratuit (SPECS.md §3.4).
         *
         * `first()` et non une collecte continue : la saisie en cours ne doit
         * jamais être écrasée par une écriture du dépôt.
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

    /** Referme le message d'erreur, sans effacer ce que l'utilisateur a saisi. */
    fun dismissFailure() = update { it.copy(failure = null) }

    /**
     * Tente la connexion.
     *
     * L'adresse est analysée d'abord : la rejeter ici évite un aller-retour
     * réseau, et surtout permet de désigner le champ fautif plutôt que
     * d'afficher une erreur générale.
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
                is AuthResult.Success ->
                    /*
                     * Le mot de passe est retiré de l'état dès qu'il a servi :
                     * un `UiState` survit à l'écran qui l'affiche, et se
                     * retrouverait dans un instantané de débogage ou une
                     * restauration de processus.
                     */
                    update { it.copy(isSubmitting = false, apiPassword = "") }

                is AuthResult.Failure ->
                    update { it.copy(isSubmitting = false, failure = LoginFailure.Server(result.error)) }
            }
        }
    }

    private fun fail(failure: LoginFailure) = update { it.copy(failure = failure) }

    /**
     * Applique une modification puis **réévalue les champs dérivés**.
     *
     * Les recalculer ici plutôt qu'à chaque appel garantit qu'ils ne peuvent
     * pas se désynchroniser de la saisie : un `copy` qui les oublierait
     * laisserait le bouton actif sur un formulaire vide.
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
