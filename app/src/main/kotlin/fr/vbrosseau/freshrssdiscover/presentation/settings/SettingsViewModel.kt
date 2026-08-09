package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.BuildConfig
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.presentation.UiStateSharing
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
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
    /**
     * Le réglage ne se contente pas d'être enregistré : il programme ou annule.
     *
     * Sans cette dépendance, éteindre le rappel laisserait le travail du
     * lendemain en attente — et il partirait quand même. Un réglage qui n'agit
     * sur rien est un défaut, pas une préférence.
     */
    private val reminderScheduler: ReminderScheduler,
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
        // Réunis parce que `combine` ne se décline que jusqu'à cinq flux, et
        // qu'ils ont le même statut : deux préférences booléennes ou presque,
        // lues sur le disque, sans lien l'une avec l'autre.
        combine(
            settingsRepository.observeFeedPresentation(),
            settingsRepository.observeReminderEnabled(),
            ::StoredPreferences,
        ),
        cacheRepository.observeCacheStatus(),
        // Les deux états purement locaux sont réunis avant d'entrer dans le
        // `combine` principal : `combine` ne se décline que jusqu'à cinq flux,
        // et les regrouper ici dit du même coup lesquels ne viennent pas du
        // disque.
        combine(signOutConfirmation, lastPurgedCount, ::TransientState),
    ) { session, settings, preferences, cache, transient ->
        stateOf(session, settings, preferences, cache, transient)
    }
        .stateIn(
            scope = viewModelScope,
            started = UiStateSharing,
            initialValue = stateOf(
                session = null,
                settings = ReadingSettings.Default,
                preferences = StoredPreferences(
                    presentation = FeedPresentation.Default,
                    // Le rappel est actif par défaut dans le dépôt : afficher
                    // « éteint » le temps de la première lecture ferait
                    // clignoter l'interrupteur à chaque ouverture de l'écran.
                    reminderEnabled = true,
                ),
                cache = CacheStatus.Empty,
                transient = TransientState(confirming = false, purged = null),
            ),
        )

    /**
     * Allume ou éteint le marquage par visibilité (SPECS.md §4.5, §6).
     *
     * Rien d'autre à faire qu'enregistrer, contrairement au rappel de lecture :
     * les deux ViewModels du flux observent déjà les réglages et reconstruisent
     * leur détecteur à chaque émission. Le changement s'applique donc sans
     * redémarrage, sans que cet écran ait à toucher quoi que ce soit du flux.
     *
     * Les deux seuils ne sont **pas** réécrits au passage : ils restent tels
     * quels pour le rallumage.
     */
    fun setAutoMarkAsReadEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoMarkAsReadEnabled(enabled) }
    }

    /** @param percent une position du curseur, donc déjà dans les bornes du domaine. */
    fun setVisibleFractionPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setVisibleFraction(percent / PERCENT) }
    }

    fun setContinuousVisibilitySeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setContinuousVisibilityMillis(seconds * MILLIS_PER_SECOND) }
    }

    /**
     * Enregistre le mode de parcours, sans le recopier dans l'état d'interface.
     *
     * Le segment sélectionné vient du dépôt : si l'écriture échouait, l'écran
     * continuerait de montrer le mode réellement appliqué plutôt qu'un choix
     * qui n'a pas pris. C'est aussi ce qui permet à SPECS.md §4.8 d'être tenu —
     * le changement s'applique **sans redémarrage**, parce que l'écran de flux
     * observe la même source que celui-ci.
     */
    fun setFeedPresentation(presentation: FeedPresentation) {
        viewModelScope.launch { settingsRepository.setFeedPresentation(presentation) }
    }

    /**
     * Enregistre le choix, **puis** programme ou annule le rappel.
     *
     * Les deux dans cet ordre et dans la même coroutine : le dépôt fait foi sur
     * ce que l'utilisateur veut, le planificateur ne fait qu'en tirer les
     * conséquences. Programmer d'abord laisserait, si l'écriture échouait, un
     * rappel armé qu'aucun réglage ne dit vouloir.
     *
     * L'état affiché, lui, n'est pas touché ici : il vient du dépôt, comme le
     * mode de présentation. Un interrupteur qui basculerait de lui-même
     * montrerait un choix que l'enregistrement n'a peut-être pas retenu.
     */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            if (enabled) reminderScheduler.scheduleNext() else reminderScheduler.cancel()
        }
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
        preferences: StoredPreferences,
        cache: CacheStatus,
        transient: TransientState,
    ): SettingsUiState = SettingsUiState(
        account = session?.let { SettingsAccount(serverAddress = it.server.baseUrl, username = it.username) },
        presentation = preferences.presentation,
        isReminderEnabled = preferences.reminderEnabled,
        isAutoMarkAsReadEnabled = settings.autoMarkAsReadEnabled,
        visibleFraction = visibleFractionThresholdOf(settings),
        continuousVisibility = continuousVisibilityThresholdOf(settings),
        cache = SettingsCache(
            articleCount = cache.articleCount,
            purgeableCount = cache.purgeableCount,
            lastPurgedCount = transient.purged,
        ),
        appVersion = BuildConfig.VERSION_NAME,
        isSignOutConfirmationVisible = transient.confirming,
    )

    /**
     * Ce que l'écran porte de lui-même : rien de tout cela n'est sur le disque.
     *
     * Les réunir n'est pas qu'une commodité pour tenir dans les cinq flux du
     * `combine` : c'est la frontière entre ce qui survit à la fermeture de
     * l'écran et ce qui disparaît avec lui.
     */
    private data class TransientState(
        val confirming: Boolean,
        val purged: Int?,
    )

    /**
     * Les préférences persistées qui ne sont pas des seuils.
     *
     * Un regroupement de commodité, et il l'assume : `combine` s'arrête à cinq
     * flux. Les deux réglages n'ont rien à voir l'un avec l'autre, ce qui est
     * précisément pourquoi ils restent deux champs et non un type du domaine.
     */
    private data class StoredPreferences(
        val presentation: FeedPresentation,
        val reminderEnabled: Boolean,
    )
}
