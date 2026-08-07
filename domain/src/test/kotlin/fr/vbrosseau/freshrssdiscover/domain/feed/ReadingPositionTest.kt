package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La règle de reprise, éprouvée sur le cas qui est en réalité le plus fréquent :
 * l'article mémorisé a **disparu**.
 *
 * Ce n'est pas une bizarrerie mais la situation ordinaire — l'article de tête
 * est celui que le marquage automatique vient de rendre lu, et le flux n'en
 * montre que des non-lus. Une reprise fondée sur le seul identifiant échouerait
 * donc presque toujours, et la fonctionnalité serait vraie sur le papier et
 * inopérante en pratique. Constaté sur un flux réel : après quatre écrans de
 * défilement, l'article mémorisé n'était plus dans les quarante premiers non-lus.
 */
class ReadingPositionTest {
    private fun candidates(vararg dates: Pair<Long, Long>) =
        dates.map { (id, date) -> ReadingPosition.Candidate(id, date) }

    @Test
    fun theExactArticleIsPreferredWhenItIsStillThere() {
        val position = ReadingPosition(ArticleId(2L), publishedAtEpochSeconds = 200L)

        val index = position.indexIn(candidates(1L to 300L, 2L to 200L, 3L to 100L))

        assertEquals(1, index)
    }

    @Test
    fun aDisappearedArticleFallsBackToTheFirstNoNewerOne() {
        // L'article 2 a été lu et n'est plus dans le flux : on reprend à celui
        // qui le suivait, pas en haut.
        val position = ReadingPosition(ArticleId(2L), publishedAtEpochSeconds = 200L)

        val index = position.indexIn(candidates(1L to 300L, 3L to 150L, 4L to 100L))

        assertEquals(1, index)
    }

    @Test
    fun anArticlePublishedAtTheExactSameSecondCounts() {
        // La frontière est inclusive : deux articles peuvent partager la
        // seconde, et exclure l'égalité sauterait celui qu'on cherche.
        val position = ReadingPosition(ArticleId(2L), publishedAtEpochSeconds = 200L)

        val index = position.indexIn(candidates(1L to 300L, 9L to 200L))

        assertEquals(1, index)
    }

    @Test
    fun aFeedMadeOnlyOfNewerArticlesResumesAtTheTop() {
        // Tout est nouveau depuis la dernière visite : le haut est la bonne
        // place, et `null` dit à l'appelant de ne rien faire.
        val position = ReadingPosition(ArticleId(2L), publishedAtEpochSeconds = 200L)

        assertNull(position.indexIn(candidates(1L to 400L, 5L to 300L)))
    }

    @Test
    fun anEmptyFeedYieldsNothing() {
        val position = ReadingPosition(ArticleId(2L), publishedAtEpochSeconds = 200L)

        assertNull(position.indexIn(emptyList()))
    }

    @Test
    fun theExactArticleWinsEvenWhenAnOlderOneComesFirst() {
        // L'ordre du flux est mélangé (§4.2) : un article plus ancien peut
        // précéder celui qu'on cherche. La correspondance exacte prime.
        val position = ReadingPosition(ArticleId(7L), publishedAtEpochSeconds = 200L)

        val index = position.indexIn(candidates(1L to 100L, 7L to 200L))

        assertEquals(1, index)
    }
}
