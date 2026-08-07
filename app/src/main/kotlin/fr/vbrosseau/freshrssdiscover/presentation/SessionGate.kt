package fr.vbrosseau.freshrssdiscover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Ce que l'application affiche à la racine.
 *
 * [Unknown] existe pour une raison précise : la session vit sur disque, et sa
 * première lecture n'est pas instantanée. Sans cet état, l'écran de connexion
 * apparaîtrait un instant à chaque lancement, y compris pour un utilisateur
 * déjà connecté.
 */
sealed interface SessionGate {
    data object Unknown : SessionGate

    data object SignedOut : SessionGate

    data object SignedIn : SessionGate
}

@HiltViewModel
class SessionGateViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val gate: StateFlow<SessionGate> = authRepository.observeSession()
        .map { session -> if (session == null) SessionGate.SignedOut else SessionGate.SignedIn }
        .stateIn(
            scope = viewModelScope,
            // `Eagerly` et non `WhileSubscribed` : l'aiguillage racine est
            // observé pendant toute la vie de l'application, et le laisser
            // retomber sur `Unknown` ferait clignoter l'écran de connexion à
            // chaque retour d'arrière-plan.
            started = SharingStarted.Eagerly,
            initialValue = SessionGate.Unknown,
        )
}
