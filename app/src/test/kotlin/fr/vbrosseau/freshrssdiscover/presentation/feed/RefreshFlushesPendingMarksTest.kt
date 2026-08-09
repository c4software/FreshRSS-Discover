package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * Le rechargement dit au serveur ce qui a été lu **avant** de l'interroger
 * (GOAL-024).
 *
 * **Le défaut que ces deux cas verrouillent, et comment il se voyait.** Les
 * marquages sont regroupés pendant cinq secondes avant d'être transmis
 * (SPECS.md §8, question 4). Un rechargement demandé dans cette fenêtre
 * interrogeait donc un serveur qui croyait encore non lus les articles qu'on
 * venait de lire : il les renvoyait, ils reprenaient leur place dans la page de
 * quarante, et les nouveaux articles n'apparaissaient pas. **Il fallait
 * recharger deux fois.** Signalé par l'auteur, puis mesuré sur l'émulateur —
 * 162 non lus avant la lecture, 162 encore à l'instant du rechargement, 158
 * douze secondes plus tard.
 *
 * **Pourquoi un crochet plutôt que deux compteurs.** `flushCallCount == 1` et
 * `refreshCallCount == 1` seraient tous deux vrais dans l'ordre fautif : deux
 * compteurs disent que deux choses ont eu lieu, jamais laquelle a précédé
 * l'autre. `FakeArticleRepository.onRefresh` observe l'état du monde **au
 * moment** où le rechargement part, ce qui est la seule façon d'éprouver un
 * ordre.
 *
 * Les deux modes ont leur cas : ils portent la même règle dans deux ViewModels
 * distincts, et un correctif appliqué d'un seul côté les ferait diverger — ce
 * qui est arrivé assez souvent dans ce dépôt pour qu'on ne s'y fie plus
 * (ARCHITECTURE.md §9.6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshFlushesPendingMarksTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    /**
     * Les marquages transmis **au moment** où le rechargement part.
     *
     * `-1` tant que le rechargement n'a pas eu lieu : une valeur qui ne peut
     * pas être confondue avec « aucun envoi », qui est zéro et serait
     * précisément le défaut.
     */
    private var flushesWhenRefreshStarted = -1

    private fun recordFlushCountAtRefresh() {
        repository.onRefresh = { flushesWhenRefreshStarted = readSyncRepository.flushCallCount }
    }

    /**
     * Le ViewModel appelle déjà `flush()` à sa construction — le rejeu au
     * démarrage de SPECS.md §4.5. C'est ce décompte-là qu'il faut retrancher,
     * sans quoi le cas passerait sur un envoi qui n'a rien à voir avec le
     * geste.
     */
    private fun flushesCausedByRefresh(atStartup: Int): Int = flushesWhenRefreshStarted - atStartup

    @Test
    fun listModeTransmitsWhatWasReadBeforeAskingTheServerAgain() {
        val viewModel = DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
        val atStartup = readSyncRepository.flushCallCount
        recordFlushCountAtRefresh()

        viewModel.refresh()

        assertEquals(
            1,
            flushesCausedByRefresh(atStartup),
            "le rechargement doit transmettre les marquages en attente avant d'interroger le serveur",
        )
    }

    @Test
    fun swipeModeTransmitsWhatWasReadBeforeAskingTheServerAgain() {
        val viewModel = SwipeViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
        val atStartup = readSyncRepository.flushCallCount
        recordFlushCountAtRefresh()

        viewModel.refresh()

        assertEquals(
            1,
            flushesCausedByRefresh(atStartup),
            "le mode Balayage porte la même règle : un correctif d'un seul côté ferait diverger les deux modes",
        )
    }
}
