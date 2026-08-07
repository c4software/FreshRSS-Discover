package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeReadingPositionRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPosition
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
        repository.position = position(42L)

        assertEquals(position(42L), viewModel.positionToRestore.value)
    }

    @Test
    fun noPositionIsOfferedWhenNoneWasStored() {
        assertNull(viewModel.positionToRestore.value)
    }

    @Test
    fun thePositionIsNotOfferedTwice() {
        // La garder en attente ferait sauter la liste au prochain chargement.
        repository.position = position(42L)
        assertEquals(position(42L), viewModel.positionToRestore.value)

        viewModel.onPositionRestored()

        assertNull(viewModel.positionToRestore.value)
    }

    @Test
    fun theFirstVisibleArticleIsRemembered() {
        viewModel.onFirstVisibleArticleChanged(ArticleId(7L), publishedAtEpochSeconds = 700L)

        assertEquals(listOf(position(7L, 700L)), repository.rememberedPositions)
    }

    @Test
    fun theSameArticleIsNeverWrittenTwice() {
        // L'écran signale cinq fois par seconde : écrire à chaque observation
        // solliciterait le disque pour rien.
        repeat(5) { viewModel.onFirstVisibleArticleChanged(ArticleId(7L), 700L) }
        viewModel.onFirstVisibleArticleChanged(ArticleId(8L), 800L)

        assertEquals(listOf(position(7L, 700L), position(8L, 800L)), repository.rememberedPositions)
    }

    @Test
    fun anAbsentFirstVisibleArticleIsIgnored() {
        // Liste vide, ou disposition pas encore mesurée : écrire effacerait une
        // position parfaitement valable.
        viewModel.onFirstVisibleArticleChanged(null, publishedAtEpochSeconds = 700L)

        assertTrue(repository.rememberedPositions.isEmpty())
    }

    @Test
    fun thePublicationDateTravelsWithTheIdentifier() {
        // Sans elle, la reprise ne peut pas retomber sur l'article le plus
        // proche — or celui qu'on retient vient d'être marqué lu et aura
        // presque toujours quitté le flux (SPECS.md §5.3).
        viewModel.onFirstVisibleArticleChanged(ArticleId(7L), publishedAtEpochSeconds = 1_700L)

        assertEquals(1_700L, repository.rememberedPositions.single().publishedAtEpochSeconds)
    }

    private fun position(id: Long, publishedAt: Long = 0L) =
        ReadingPosition(ArticleId(id), publishedAtEpochSeconds = publishedAt)
}
