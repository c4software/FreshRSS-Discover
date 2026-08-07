package fr.vbrosseau.freshrssdiscover.domain.shuffle

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.feed.feedRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Une date de référence quelconque : seul l'écart entre articles compte ici. */
private const val REFERENCE_EPOCH = 1_700_000_000L

/** Une heure, en secondes : le pas entre deux articles « du jour ». */
private const val ONE_HOUR = 3_600L

/** Trente jours, en secondes : l'« article vieux d'un mois » de SPECS.md §4.2. */
private const val ONE_MONTH = 30 * 24 * ONE_HOUR

/**
 * Recopie volontaire de la fenêtre du mélangeur, qui est privée : le test
 * énonce la garantie attendue, il ne la lit pas dans le code éprouvé.
 */
private const val LOOKAHEAD_WINDOW = 8

/**
 * Le mélange est la seule règle réellement subtile de l'application (SPECS.md
 * §4.2) et ses quatre exigences se contredisent partiellement : ces tests
 * fixent l'arbitrage retenu, faute de quoi un ajustement futur de la fenêtre
 * passerait inaperçu.
 */
class InterleaveBySourceTest {
    private fun item(
        id: Long,
        feed: String,
        publishedAt: Long = REFERENCE_EPOCH - id * ONE_HOUR,
    ): Article =
        article(
            id = id,
            publishedAtEpochSeconds = publishedAt,
            feed = feedRef(id = "feed/$feed", title = "Flux $feed"),
        )

    /** Un article par lettre, dans l'ordre donné : la forme la plus lisible d'un cas. */
    private fun articlesOf(feeds: String): List<Article> =
        feeds.mapIndexed { index, feed -> item(id = index.toLong(), feed = feed.toString()) }

    private fun feedsOf(articles: List<Article>): String =
        articles.joinToString(separator = "") { it.feed.id.removePrefix("feed/") }

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals(emptyList(), interleaveBySource(emptyList()))
    }

    @Test
    fun aSingleArticleIsReturnedAsIs() {
        val articles = articlesOf("A")

        assertEquals(articles, interleaveBySource(articles))
    }

    @Test
    fun aSingleSourceKeepsTheServerOrder() {
        // Rien à entrelacer : réordonner ici ne ferait que casser la chronologie.
        val articles = articlesOf("AAAAA")

        assertEquals(articles, interleaveBySource(articles))
    }

    @Test
    fun twoAlreadyAlternatingSourcesAreLeftUntouched() {
        val articles = articlesOf("ABABAB")

        assertEquals(articles, interleaveBySource(articles))
    }

    @Test
    fun twoGroupedSourcesGetAlternated() {
        val articles = articlesOf("AAABBB")

        assertEquals("ABABAB", feedsOf(interleaveBySource(articles)))
    }

    @Test
    fun aProlificFeedDoesNotKeepConsecutivePositionsWhileAnotherSourceExists() {
        // A domine largement ; B et C sont rares mais présents dans la fenêtre.
        val articles = articlesOf("AAAAABAAACAA")

        val ordered = interleaveBySource(articles)

        assertEquals("ABACA", feedsOf(ordered).take(5))
    }

    @Test
    fun theSameSourceFollowsItselfOnlyWhenNoOtherRemains() {
        val articles = articlesOf("AAAAABAAACAA")

        val ordered = interleaveBySource(articles)

        // Les répétitions ne commencent qu'une fois B et C consommés.
        val firstRepetition = ordered.indices.first { it > 0 && ordered[it].feed == ordered[it - 1].feed }
        val consumed = ordered.take(firstRepetition).map { it.feed.id }
        assertTrue("feed/B" in consumed && "feed/C" in consumed, "répétition prématurée dans ${feedsOf(ordered)}")
    }

    @Test
    fun anImpossibleInterleavingStillYieldsTheWholeInput() {
        // Tout vient du même flux : la règle 1 n'est pas satisfiable, la sortie
        // reste néanmoins complète et chronologique.
        val articles = articlesOf("AAAA")

        assertEquals(articles, interleaveBySource(articles, previousTail = listOf(item(id = 99L, feed = "A"))))
    }

    @Test
    fun theOutputIsAnExactPermutationOfTheInput() {
        val articles = articlesOf("ABBACCBAABCCBAAB")

        val ordered = interleaveBySource(articles)

        assertEquals(articles.size, ordered.size)
        assertEquals(articles.map { it.id }.sortedBy { it.value }, ordered.map { it.id }.sortedBy { it.value })
    }

    @Test
    fun twoCallsOnTheSameInputProduceTheSameOrder() {
        val articles = articlesOf("AABCAAABCAAAB")
        val tail = listOf(item(id = 99L, feed = "A"))

        assertEquals(interleaveBySource(articles, tail), interleaveBySource(articles, tail))
    }

    @Test
    fun aMonthOldArticleNeverReachesTheTopOfTheFeed() {
        // Trente articles du jour, tous du même flux, puis un article vieux d'un
        // mois : sans borne, la lutte contre la monotonie le remonterait en tête.
        val recentCount = 30
        val recent = (0 until recentCount).map { item(id = it.toLong(), feed = "A") }
        val old = item(id = recentCount.toLong(), feed = "B", publishedAt = REFERENCE_EPOCH - ONE_MONTH)

        val ordered = interleaveBySource(recent + old)

        val position = ordered.indexOf(old)
        val earliestAllowed = recentCount - (LOOKAHEAD_WINDOW - 1)
        assertTrue(position >= earliestAllowed, "l'article ancien est remonté en position $position")
        assertTrue(ordered[0].publishedAtEpochSeconds > old.publishedAtEpochSeconds)
    }

    @Test
    fun noArticleIsAdvancedBeyondTheLookaheadWindow() {
        val articles = articlesOf("AAABAAAAACAAAABAAAAC")

        val ordered = interleaveBySource(articles)

        ordered.forEachIndexed { position, moved ->
            val origin = articles.indexOf(moved)
            assertTrue(
                position >= origin - (LOOKAHEAD_WINDOW - 1),
                "article $origin présenté en position $position",
            )
        }
    }

    @Test
    fun theFirstArticleAvoidsTheFeedThatClosedThePreviousPage() {
        val articles = articlesOf("AAB")

        val ordered = interleaveBySource(articles, previousTail = listOf(item(id = 99L, feed = "A")))

        assertEquals("BAA", feedsOf(ordered))
    }

    @Test
    fun anEmptyPreviousTailImposesNothing() {
        val articles = articlesOf("AAB")

        assertEquals("ABA", feedsOf(interleaveBySource(articles)))
    }
}
