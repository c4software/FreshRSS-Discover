package fr.vbrosseau.freshrssdiscover.domain.shuffle

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.feed.feedRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Arbitrary reference date: only the gap between articles matters here. */
private const val REFERENCE_EPOCH = 1_700_000_000L

/** One hour, in seconds: the step between two same-day articles. */
private const val ONE_HOUR = 3_600L

/** Thirty days, in seconds: the "month-old article" of SPECS.md §4.2. */
private const val ONE_MONTH = 30 * 24 * ONE_HOUR

/**
 * Deliberate copy of the shuffler's window, which is private: the test states
 * the expected guarantee, it does not read it from the code under test.
 */
private const val LOOKAHEAD_WINDOW = 8

/**
 * The shuffle is the app's one genuinely subtle rule (SPECS.md §4.2) and its
 * four requirements partially contradict each other: these tests pin down the
 * chosen trade-off, without which a future window adjustment would go
 * unnoticed.
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

    /** One article per letter, in the given order: the most readable form of a case. */
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
        // Nothing to interleave: reordering here would only break chronology.
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
        // A dominates heavily; B and C are rare but present in the window.
        val articles = articlesOf("AAAAABAAACAA")

        val ordered = interleaveBySource(articles)

        assertEquals("ABACA", feedsOf(ordered).take(5))
    }

    @Test
    fun theSameSourceFollowsItselfOnlyWhenNoOtherRemains() {
        val articles = articlesOf("AAAAABAAACAA")

        val ordered = interleaveBySource(articles)

        // Repetitions only start once B and C are consumed.
        val firstRepetition = ordered.indices.first { it > 0 && ordered[it].feed == ordered[it - 1].feed }
        val consumed = ordered.take(firstRepetition).map { it.feed.id }
        assertTrue("feed/B" in consumed && "feed/C" in consumed, "répétition prématurée dans ${feedsOf(ordered)}")
    }

    @Test
    fun anImpossibleInterleavingStillYieldsTheWholeInput() {
        // Everything comes from the same feed: rule 1 is unsatisfiable, yet
        // the output stays complete and chronological.
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
        // Thirty same-day articles, all from one feed, then a month-old
        // article: without a bound, the fight against monotony would promote
        // it to the top.
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
