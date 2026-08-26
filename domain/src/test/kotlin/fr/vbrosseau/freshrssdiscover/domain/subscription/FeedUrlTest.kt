package fr.vbrosseau.freshrssdiscover.domain.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class FeedUrlTest {
    private fun valid(raw: String): String = assertIs<FeedUrlResult.Valid>(FeedUrl.parse(raw)).url.value

    @Test
    fun aFullAddressIsKeptAsTyped() {
        // The URL is the server's key for the subscription: rewriting it
        // would make the listed address differ from the typed one.
        assertEquals("https://xkcd.com/atom.xml", valid("https://xkcd.com/atom.xml"))
        assertEquals("http://exemple.org/rss?format=Atom&x=1", valid("http://exemple.org/rss?format=Atom&x=1"))
    }

    @Test
    fun surroundingWhitespaceIsDropped() {
        assertEquals("https://xkcd.com/atom.xml", valid("  https://xkcd.com/atom.xml\n"))
    }

    @Test
    fun aMissingSchemeIsAssumedToBeHttps() {
        // Same rule as the sign-in screen (SPECS.md §3.1): nobody types the
        // scheme of a feed found on a page.
        assertEquals("https://xkcd.com/atom.xml", valid("xkcd.com/atom.xml"))
    }

    @Test
    fun plainHttpStaysAccepted() {
        // Self-hosted feeds on a local network exist, as for the server.
        assertEquals("http://192.168.1.10/rss.xml", valid("http://192.168.1.10/rss.xml"))
    }

    @Test
    fun theSchemeIsMatchedRegardlessOfCase() {
        assertEquals("HTTPS://xkcd.com/atom.xml", valid("HTTPS://xkcd.com/atom.xml"))
    }

    @Test
    fun anEmptyOrBlankFieldIsBlankNotInvalid() {
        // Two different words on screen: nothing typed is not a mistake.
        assertEquals(FeedUrlResult.Blank, FeedUrl.parse(""))
        assertEquals(FeedUrlResult.Blank, FeedUrl.parse("   \t"))
    }

    @Test
    fun aSchemeTheServerWillNotFetchIsInvalid() {
        assertEquals(FeedUrlResult.Invalid, FeedUrl.parse("feed://xkcd.com/atom.xml"))
        assertEquals(FeedUrlResult.Invalid, FeedUrl.parse("ftp://exemple.org/rss"))
    }

    @Test
    fun anAddressWithoutAHostIsInvalid() {
        assertEquals(FeedUrlResult.Invalid, FeedUrl.parse("https://"))
        assertEquals(FeedUrlResult.Invalid, FeedUrl.parse("https:///atom.xml"))
    }

    @Test
    fun anUnparsableAddressIsInvalidRatherThanThrown() {
        assertEquals(FeedUrlResult.Invalid, FeedUrl.parse("https://xkcd .com/atom.xml"))
        assertEquals(FeedUrlResult.Invalid, FeedUrl.parse("pas une adresse du tout"))
    }

    @Test
    fun twoAddressesAreEqualByValue() {
        val first = assertIs<FeedUrlResult.Valid>(FeedUrl.parse("xkcd.com/atom.xml")).url
        val second = assertIs<FeedUrlResult.Valid>(FeedUrl.parse("https://xkcd.com/atom.xml")).url

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals("FeedUrl(https://xkcd.com/atom.xml)", first.toString())
        assertNotEquals(first, assertIs<FeedUrlResult.Valid>(FeedUrl.parse("https://xkcd.com/rss.xml")).url)
    }
}
