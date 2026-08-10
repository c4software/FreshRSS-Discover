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
 * What the application displays at the root.
 *
 * [Unknown] exists because the session lives on disk and its first read is
 * not instantaneous. Without this state, the login screen would flash at
 * every launch, including for an already signed-in user.
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
            // `Eagerly`, not `WhileSubscribed`: the root gate is observed for
            // the whole life of the app, and letting it fall back to `Unknown`
            // would flash the login screen on every return from background.
            started = SharingStarted.Eagerly,
            initialValue = SessionGate.Unknown,
        )
}
