package fr.vbrosseau.freshrssdiscover.presentation

import kotlinx.coroutines.flow.SharingStarted

/**
 * Sharing policy common to all ViewModel `uiState` flows.
 *
 * `WhileSubscribed` stops upstream observation (network callbacks, continuous
 * Room reads) as soon as no screen collects: a live ViewModel does not
 * justify keeping system callbacks registered while the app is in the
 * background. The five-second grace period covers a rotation or a brief
 * visit to another destination without re-registering everything.
 */
val UiStateSharing: SharingStarted = SharingStarted.WhileSubscribed(5_000)
