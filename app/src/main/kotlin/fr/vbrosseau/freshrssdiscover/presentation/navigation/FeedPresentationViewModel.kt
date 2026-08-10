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
 * The chosen presentation mode (SPECS.md §4.8), for navigation only.
 *
 * `SettingsViewModel` already exposes it, but instantiating it from the feed
 * destination would pull sign-out, cache purge, and read thresholds into a
 * screen that needs none of them, and destroying it would take unrelated
 * state along. This ViewModel observes a single value and modifies none: mode
 * changes remain the settings screen's job.
 */
@HiltViewModel
class FeedPresentationViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /**
     * Starts on [FeedPresentation.Default] rather than a waiting `null`.
     *
     * Reading the DataStore is near-immediate but not synchronous: waiting
     * would show an empty screen at launch, whereas List mode is the right
     * bet; it is the default and what anyone who never opened settings sees.
     */
    val presentation: StateFlow<FeedPresentation> =
        settingsRepository.observeFeedPresentation()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = FeedPresentation.Default,
            )

    private companion object {
        /** Long enough to span a rotation, avoiding a disk re-read on each recreation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
