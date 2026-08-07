package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.BuildConfig
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.presentation.UiStateSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * TODO(GOAL-011) : ces deux valeurs recopient les défauts de `ReadDetector`
 *  parce qu'aucun stockage de réglages n'existe encore. Tant qu'il manque, les
 *  seuils de SPECS.md §4.5 sont affichés mais ni modifiables ni persistés, et
 *  rien n'empêche les deux déclarations de diverger.
 */
private const val DEFAULT_VISIBLE_FRACTION_PERCENT = 60
private const val DEFAULT_CONTINUOUS_VISIBILITY_SECONDS = 1

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    /**
     * Séparée de la session : la confirmation est un état d'interface, la
     * session vient du disque. Les combiner dans un seul `MutableStateFlow`
     * obligerait à réécrire la session à chaque ouverture de la boîte de
     * dialogue, donc à la dupliquer.
     */
    private val signOutConfirmation = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.observeSession(),
        signOutConfirmation,
    ) { session, confirming -> stateOf(session, confirming) }
        .stateIn(
            scope = viewModelScope,
            started = UiStateSharing,
            initialValue = stateOf(session = null, confirming = false),
        )

    /**
     * Demande la confirmation plutôt que de déconnecter.
     *
     * SPECS.md §3.5 l'impose : l'action efface le jeton **et** le cache, elle
     * n'est pas rattrapable par un simple retour en arrière.
     */
    fun requestSignOut() {
        signOutConfirmation.value = true
    }

    fun dismissSignOut() {
        signOutConfirmation.value = false
    }

    /**
     * La confirmation est refermée avant l'appel, et non après.
     *
     * `signOut()` fait tomber la session, ce qui ramène l'utilisateur à l'écran
     * de connexion : attendre son retour laisserait la boîte de dialogue
     * visible pendant la transition.
     */
    fun confirmSignOut() {
        signOutConfirmation.value = false
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun stateOf(session: AuthSession?, confirming: Boolean): SettingsUiState = SettingsUiState(
        account = session?.let { SettingsAccount(serverAddress = it.server.baseUrl, username = it.username) },
        visibleFractionPercent = DEFAULT_VISIBLE_FRACTION_PERCENT,
        continuousVisibilitySeconds = DEFAULT_CONTINUOUS_VISIBILITY_SECONDS,
        appVersion = BuildConfig.VERSION_NAME,
        isSignOutConfirmationVisible = confirming,
    )
}
