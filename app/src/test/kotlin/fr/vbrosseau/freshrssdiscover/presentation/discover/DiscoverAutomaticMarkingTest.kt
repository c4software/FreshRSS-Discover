package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOW_MILLIS = 1_700_000_000_000L

/** Durée d'affichage continu exigée par SPECS.md §4.5, reprise pour la lisibilité des cas. */
private const val VISIBILITY_THRESHOLD_MILLIS = 1_000L

/**
 * Ce que l'interrupteur du marquage automatique arrête, et ce qu'il n'arrête
 * pas (SPECS.md §4.5, §4.7), en mode Liste.
 *
 * Fichier distinct de `DiscoverViewModelTest` : celui-ci a atteint la taille
 * que Detekt refuse de dépasser, et le réglage forme un sujet à lui — un état
 * de départ commun à tous ses cas, que les autres n'emploient jamais.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverAutomaticMarkingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    /** Paresseux : le ViewModel charge dès sa création, sur `Dispatchers.Main`. */
    private val viewModel: DiscoverViewModel by lazy {
        DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private val state get() = viewModel.uiState.value

    private val readArticles: Set<ArticleId> get() = readSyncRepository.markCalls.flatten().toSet()

    /** Amène [id] au-delà des deux seuils, en deux observations séparées d'une seconde. */
    private fun watch(id: Long) {
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to 1f))
    }

    @Test
    fun withAutomaticMarkingOnAVisibleArticleBecomesRead() {
        // Le témoin : sans lui, les cas suivants passeraient aussi bien si le
        // marquage était cassé pour tout le monde.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        watch(id = 1L)

        assertEquals(setOf(ArticleId(1L)), readArticles)
    }

    @Test
    fun withAutomaticMarkingOffAVisibleArticleStaysUnread() {
        // C'est l'objet du réglage : parcourir son flux sans le consommer.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        watch(id = 1L)

        assertEquals(emptySet(), readArticles)
        assertFalse(state.articles.single().isRead)
    }

    @Test
    fun withAutomaticMarkingOffOpeningAnArticleStillMarksItRead() {
        // Le piège de ce Goal : l'interrupteur n'arrête que la détection par
        // visibilité. Ouvrir un article reste un geste délibéré (SPECS.md §4.7)
        // et le marque, sans quoi le flux le représenterait indéfiniment.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        assertTrue(viewModel.onArticleOpened(1L))

        assertEquals(setOf(ArticleId(1L)), readArticles)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun turningAutomaticMarkingBackOnResumesWithoutARestart() {
        // SPECS.md §6 : le réglage s'applique sans redémarrage, par le flux des
        // réglages déjà observé — et non par une seconde source à surveiller.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)
        watch(id = 1L)

        settingsRepository.setAutomaticMarking(enabled = true)
        watch(id = 1L)

        assertEquals(setOf(ArticleId(1L)), readArticles)
    }

    @Test
    fun turningAutomaticMarkingOffMidWatchDropsTheRunningCountdown() {
        // Le chronomètre démarré sous l'ancien réglage ne survit pas à
        // l'extinction : le laisser aboutir marquerait un article après que
        // l'utilisateur a demandé que cela cesse.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)

        settingsRepository.setAutomaticMarking(enabled = false)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(emptySet(), readArticles)
    }
}
