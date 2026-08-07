package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.BuildConfig
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.presentation.UiStateSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PERCENT = 100f
private const val MILLIS_PER_SECOND = 1_000L

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
) : ViewModel() {
    /**
     * Séparée de la session : la confirmation est un état d'interface, la
     * session vient du disque. Les combiner dans un seul `MutableStateFlow`
     * obligerait à réécrire la session à chaque ouverture de la boîte de
     * dialogue, donc à la dupliquer.
     */
    private val signOutConfirmation = MutableStateFlow(false)

    /**
     * Résultat de la dernière purge manuelle, `null` tant qu'il n'y en a pas eu.
     *
     * Il ne peut pas venir du dépôt : celui-ci publie ce que le cache contient,
     * pas ce qu'un geste vient d'en retirer. Or c'est bien cette différence
     * qu'il faut montrer — sans confirmation préalable, le compte rendu est le
     * seul retour que l'utilisateur obtient de son appui.
     */
    private val lastPurgedCount = MutableStateFlow<Int?>(null)

    /**
     * Les seuils affichés viennent du dépôt, jamais d'une copie locale.
     *
     * C'est le cœur de GOAL-011-T04 : la valeur montrée est celle qui sera
     * relue par le détecteur de lecture. Une modification n'est pas appliquée
     * à l'état d'interface puis enregistrée « aussi » — elle est enregistrée,
     * et l'affichage suit parce qu'il observe la même source. Les deux ne
     * peuvent donc pas diverger, même si une écriture échoue.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.observeSession(),
        settingsRepository.observeReadingSettings(),
        signOutConfirmation,
        cacheRepository.observeCacheStatus(),
        lastPurgedCount,
    ) { session, settings, confirming, cache, purged -> stateOf(session, settings, confirming, cache, purged) }
        .stateIn(
            scope = viewModelScope,
            started = UiStateSharing,
            initialValue = stateOf(
                session = null,
                settings = ReadingSettings.Default,
                confirming = false,
                cache = CacheStatus.Empty,
                purged = null,
            ),
        )

    /** @param percent une position du curseur, donc déjà dans les bornes du domaine. */
    fun setVisibleFractionPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setVisibleFraction(percent / PERCENT) }
    }

    fun setContinuousVisibilitySeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setContinuousVisibilityMillis(seconds * MILLIS_PER_SECOND) }
    }

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

    /**
     * Purge le cache **sans rien demander**.
     *
     * Ce que le geste détruit — des articles lus et déjà connus du serveur
     * comme lus (SPECS.md §5.4) — ne justifie pas la confirmation qu'exige la
     * déconnexion, qui emporte le jeton, les non-lus et les marquages en
     * attente. Le compte rendu tient lieu de retour : voir [lastPurgedCount].
     */
    fun purgeCache() {
        viewModelScope.launch { lastPurgedCount.value = cacheRepository.purgeReadArticles() }
    }

    private fun stateOf(
        session: AuthSession?,
        settings: ReadingSettings,
        confirming: Boolean,
        cache: CacheStatus,
        purged: Int?,
    ): SettingsUiState = SettingsUiState(
        account = session?.let { SettingsAccount(serverAddress = it.server.baseUrl, username = it.username) },
        visibleFraction = visibleFractionThresholdOf(settings),
        continuousVisibility = continuousVisibilityThresholdOf(settings),
        cache = SettingsCache(
            articleCount = cache.articleCount,
            purgeableCount = cache.purgeableCount,
            lastPurgedCount = purged,
        ),
        appVersion = BuildConfig.VERSION_NAME,
        isSignOutConfirmationVisible = confirming,
    )
}
