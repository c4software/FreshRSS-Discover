package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeReadingPositionRepository
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPECS.md §5.3 : une fermeture n'est pas une demande de l'utilisateur. Lui
 * reprendre sa place à chaque retour rendrait un flux continu et sans repère
 * impraticable.
 */
class ReadingPositionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReadingPositionRepository()

    /** Construit paresseusement : l'`init` lit la position sur `Dispatchers.Main`. */
    private val viewModel: ReadingPositionViewModel by lazy { ReadingPositionViewModel(repository) }

    @Test
    fun theStoredPositionIsOfferedAtStartup() {
        repository.position = ArticleId(42L)

        assertEquals(42L, viewModel.restoreToArticleId.value)
    }

    @Test
    fun noPositionIsOfferedWhenNoneWasStored() {
        assertNull(viewModel.restoreToArticleId.value)
    }

    @Test
    fun thePositionIsNotOfferedTwice() {
        // La garder en attente ferait sauter la liste au prochain chargement.
        repository.position = ArticleId(42L)
        assertEquals(42L, viewModel.restoreToArticleId.value)

        viewModel.onPositionRestored()

        assertNull(viewModel.restoreToArticleId.value)
    }

    @Test
    fun theFirstVisibleArticleIsRemembered() {
        viewModel.onFirstVisibleArticleChanged(ArticleId(7L))

        assertEquals(listOf(ArticleId(7L)), repository.rememberedPositions)
    }

    @Test
    fun theSameArticleIsNeverWrittenTwice() {
        // L'écran signale cinq fois par seconde : écrire à chaque observation
        // solliciterait le disque pour rien.
        repeat(5) { viewModel.onFirstVisibleArticleChanged(ArticleId(7L)) }
        viewModel.onFirstVisibleArticleChanged(ArticleId(8L))

        assertEquals(listOf(ArticleId(7L), ArticleId(8L)), repository.rememberedPositions)
    }

    @Test
    fun anAbsentFirstVisibleArticleIsIgnored() {
        // Liste vide, ou disposition pas encore mesurée : écrire effacerait une
        // position parfaitement valable.
        viewModel.onFirstVisibleArticleChanged(null)

        assertTrue(repository.rememberedPositions.isEmpty())
    }
}
