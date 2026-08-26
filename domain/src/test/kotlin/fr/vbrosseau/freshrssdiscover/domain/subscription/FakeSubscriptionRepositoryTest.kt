package fr.vbrosseau.freshrssdiscover.domain.subscription

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.core.errorOrNull
import fr.vbrosseau.freshrssdiscover.domain.core.valueOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The fake will exercise the screen. These tests pin down what that screen
 * relies on: an addition shows up in the next listing, a removal leaves it,
 * and a scripted failure hits one call only.
 */
class FakeSubscriptionRepositoryTest {
    private val xkcd = Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml")
    private val repository = FakeSubscriptionRepository(listOf(xkcd))

    private fun url(raw: String): FeedUrl = assertIs<FeedUrlResult.Valid>(FeedUrl.parse(raw)).url

    @Test
    fun listingReturnsTheInitialSubscriptionsAndCountsTheCall() =
        runTest {
            assertEquals(listOf(xkcd), repository.list().valueOrNull())
            assertEquals(1, repository.listCallCount)
        }

    @Test
    fun anAdditionAppearsInTheNextListingWithAFreshIdentifier() =
        runTest {
            repository.subscribe(url("https://exemple.org/rss"))

            val listed = repository.list().valueOrNull().orEmpty()
            assertEquals(2, listed.size)
            assertEquals(SubscriptionId(4L), listed.last().id)
            assertEquals("https://exemple.org/rss", listed.last().url)
            assertEquals(listOf(url("https://exemple.org/rss")), repository.subscribedUrls)
        }

    @Test
    fun aRemovalLeavesTheNextListing() =
        runTest {
            assertIs<Outcome.Success<Unit>>(repository.unsubscribe(xkcd.id))

            assertEquals(emptyList(), repository.current)
            assertEquals(listOf(xkcd.id), repository.unsubscribedIds)
        }

    @Test
    fun removingAnUnknownIdentifierIsRefusedLikeTheServerDoes() =
        runTest {
            assertEquals(SubscriptionError.Rejected, repository.unsubscribe(SubscriptionId(99L)).errorOrNull())
        }

    @Test
    fun aScriptedFailureHitsTheNextCallOnly() =
        runTest {
            // The property the screen relies on: a retry after a failure can
            // succeed, so the failure must not stick.
            repository.nextFailure = SubscriptionError.NoNetwork

            assertEquals(SubscriptionError.NoNetwork, repository.subscribe(url("exemple.org/rss")).errorOrNull())
            assertNull(repository.nextFailure)
            assertEquals(listOf(xkcd), repository.current)
            assertIs<Outcome.Success<Unit>>(repository.subscribe(url("exemple.org/rss")))
        }

    @Test
    fun aScriptedFailureAlsoHitsAListingOrARemoval() =
        runTest {
            repository.nextFailure = SubscriptionError.ServerUnreachable
            assertEquals(SubscriptionError.ServerUnreachable, repository.list().errorOrNull())

            repository.nextFailure = SubscriptionError.SessionExpired
            assertEquals(SubscriptionError.SessionExpired, repository.unsubscribe(xkcd.id).errorOrNull())
            assertEquals(listOf(xkcd), repository.current)
        }

    @Test
    fun anEmptyRepositoryHandsOutTheFirstIdentifier() =
        runTest {
            val empty = FakeSubscriptionRepository()

            empty.subscribe(url("exemple.org/rss"))

            assertEquals(SubscriptionId(1L), empty.current.single().id)
        }
}
