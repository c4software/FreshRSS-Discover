package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * La fin de page voyage avec l'appelant (GOAL-029) : elle vit dans le moteur,
 * avec le curseur, et non dans le dépôt — un singleton que les deux modes de
 * présentation partagent, où elle mélangeait les parcours de l'un et de
 * l'autre. Éprouvé sur le mode Liste ; le moteur étant commun, le Balayage
 * suit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedPaginationTailTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val repository = FakeArticleRepository()

    /** Paresseux : le ViewModel charge dès sa création, sur `Dispatchers.Main`. */
    private val viewModel: DiscoverViewModel by lazy {
        DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = FakeReadSyncRepository(),
            settingsRepository = FakeSettingsRepository(),
            freshnessRepository = FakeFeedFreshnessRepository(),
            clock = FakeClock(NOW_MILLIS),
        )
    }

    @Test
    fun theNextPageCarriesTheTailOfThePreviousOne() {
        // Règle 4 de SPECS.md §4.2 : la jonction se juge contre le dernier
        // article rendu.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 3L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(listOf(2L), repository.requestedTails.last().map { it.id.value })
    }

    @Test
    fun afterAReloadTheTailIsTheOneOfTheRefreshedPage() {
        // La queue suit le parcours que suit le curseur : après un
        // rechargement, la page suivante prolonge la page rafraîchie.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 5L), article(id = 6L)), nextCursor = PageCursor("c9"))
        repository.enqueuePage(listOf(article(id = 7L)), nextCursor = null)

        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(listOf(6L), repository.requestedTails.last().map { it.id.value })
    }
}
