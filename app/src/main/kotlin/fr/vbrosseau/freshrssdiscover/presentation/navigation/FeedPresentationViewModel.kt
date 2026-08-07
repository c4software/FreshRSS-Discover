package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Le mode de présentation choisi (SPECS.md §4.8), pour la navigation seule.
 *
 * `SettingsViewModel` l'expose déjà, mais l'instancier depuis la destination du
 * flux ferait entrer dans celle-ci la déconnexion, la purge du cache et les
 * seuils de lecture — un écran qui n'en a que faire, et un objet dont la
 * destruction emporterait des états sans rapport. Ce ViewModel-ci n'observe
 * qu'une valeur et n'en modifie aucune : le changement de mode reste l'affaire
 * des réglages.
 */
@HiltViewModel
class FeedPresentationViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /**
     * Démarre sur [FeedPresentation.Default] plutôt que sur un `null` d'attente.
     *
     * La lecture du DataStore est quasi immédiate, mais elle n'est pas
     * synchrone : attendre afficherait un écran vide au lancement, alors que le
     * mode Liste est le bon pari — c'est le défaut, et c'est ce que voit
     * quiconque n'a jamais ouvert les réglages.
     */
    val presentation: StateFlow<FeedPresentation> =
        settingsRepository.observeFeedPresentation()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = FeedPresentation.Default,
            )

    private companion object {
        /** Le temps d'une rotation, pour ne pas relire le disque à chaque recréation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
