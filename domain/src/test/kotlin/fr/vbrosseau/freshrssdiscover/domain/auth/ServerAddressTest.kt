package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * This normalization is the user's first contact with the app and the most
 * exposed to typos. It is pure: covering it exhaustively costs nothing and
 * avoids a network round trip per edge case.
 */
class ServerAddressTest {
    private fun parsed(raw: String): ServerAddress {
        val result = ServerAddress.parse(raw)
        assertTrue(result is ServerAddressResult.Valid, "attendu valide, obtenu $result")
        return result.address
    }

    @Test
    fun aBareDomainNameGetsHttps() {
        // The most common input. Without an implicit scheme, `URI` sees no
        // host and everything would fail on the first attempt.
        val address = parsed("rss.exemple.org")

        assertEquals("https://rss.exemple.org", address.baseUrl)
        assertTrue(address.isSecure)
    }

    @Test
    fun theApiEndpointIsDerivedNeverTyped() {
        assertEquals(
            "https://rss.exemple.org/api/greader.php",
            parsed("rss.exemple.org").apiEndpoint,
        )
    }

    @Test
    fun anExplicitHttpsSchemeIsKept() {
        assertEquals("https://exemple.org", parsed("https://exemple.org").baseUrl)
    }

    @Test
    fun httpIsAcceptedButFlaggedAsInsecure() {
        // Self-hosted instances on a local network are a real case; rejecting
        // them would make the app unusable there.
        val address = parsed("http://192.168.1.20:8080")

        assertEquals("http://192.168.1.20:8080", address.baseUrl)
        assertFalse(address.isSecure)
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        // Copy-pasting from an email often carries whitespace along.
        assertEquals("https://exemple.org", parsed("  https://exemple.org \n").baseUrl)
    }

    @Test
    fun trailingSlashesAreRemoved() {
        assertEquals("https://exemple.org", parsed("https://exemple.org///").baseUrl)
    }

    @Test
    fun aSubdirectoryInstallationIsPreserved() {
        // FreshRSS is commonly installed in a subdirectory; losing it would
        // make every call return 404.
        assertEquals("https://exemple.org/freshrss", parsed("exemple.org/freshrss/").baseUrl)
        assertEquals(
            "https://exemple.org/freshrss/api/greader.php",
            parsed("exemple.org/freshrss/").apiEndpoint,
        )
    }

    @Test
    fun aPastedApiUrlIsAcceptedAndReducedToItsRoot() {
        // This URL appears in the FreshRSS documentation and in other
        // clients' configuration, so pasting it is natural. Concatenating it
        // as-is would yield `…/greader.php/api/greader.php`.
        val address = parsed("https://exemple.org/api/greader.php")

        assertEquals("https://exemple.org", address.baseUrl)
        assertEquals("https://exemple.org/api/greader.php", address.apiEndpoint)
    }

    @Test
    fun aPastedApiUrlWithTrailingSlashIsAlsoReduced() {
        assertEquals("https://exemple.org", parsed("https://exemple.org/api/greader.php/").baseUrl)
    }

    @Test
    fun aPastedApiUrlUnderASubdirectoryIsReducedToThatSubdirectory() {
        assertEquals(
            "https://exemple.org/rss",
            parsed("https://exemple.org/rss/api/greader.php").baseUrl,
        )
    }

    @Test
    fun theHostIsLowercasedButThePathIsNot() {
        // A hostname is case-insensitive; a path is not, and lowercasing it
        // would break `/FreshRSS`.
        assertEquals("https://exemple.org/FreshRSS", parsed("HTTPS://Exemple.ORG/FreshRSS").baseUrl)
    }

    @Test
    fun theDefaultPortOfTheSchemeIsDropped() {
        // Otherwise `exemple.org` and `exemple.org:443` would produce two
        // distinct addresses, hence two distinct sessions for one server.
        assertEquals("https://exemple.org", parsed("https://exemple.org:443").baseUrl)
        assertEquals("http://exemple.org", parsed("http://exemple.org:80").baseUrl)
    }

    @Test
    fun aNonDefaultPortIsKept() {
        assertEquals("https://exemple.org:8443", parsed("https://exemple.org:8443").baseUrl)
    }

    @Test
    fun theDefaultPortOfTheOtherSchemeIsNotDropped() {
        // 80 is not the default port for https: removing it would change the
        // destination.
        assertEquals("https://exemple.org:80", parsed("https://exemple.org:80").baseUrl)
    }

    @Test
    fun anEmptyInputIsReportedAsBlank() {
        assertEquals(ServerAddressResult.Blank, ServerAddress.parse(""))
        assertEquals(ServerAddressResult.Blank, ServerAddress.parse("   \t\n "))
    }

    @Test
    fun anUnsupportedSchemeIsNamedInTheResult() {
        // Naming the scheme allows a useful message instead of a generic
        // "invalid address".
        assertEquals(
            ServerAddressResult.UnsupportedScheme("ftp"),
            ServerAddress.parse("ftp://exemple.org"),
        )
        assertEquals(
            ServerAddressResult.UnsupportedScheme("file"),
            ServerAddress.parse("FILE://exemple.org"),
        )
    }

    @Test
    fun anInputWithoutAnyUsableHostIsMalformed() {
        assertEquals(ServerAddressResult.Malformed, ServerAddress.parse("https://"))
        assertEquals(ServerAddressResult.Malformed, ServerAddress.parse("http:///chemin"))
    }

    @Test
    fun anInputThatIsNotAUriAtAllIsMalformed() {
        // Internal spaces and unbalanced brackets make `URI` throw; `parse`
        // catches that instead of letting it propagate.
        assertEquals(ServerAddressResult.Malformed, ServerAddress.parse("ex emple.org"))
        assertEquals(ServerAddressResult.Malformed, ServerAddress.parse("http://[oups"))
    }

    @Test
    fun twoAddressesAreEqualWhenTheyDesignateTheSameInstance() {
        // This equality decides whether a stored session matches the server
        // the user typed.
        assertEquals(parsed("exemple.org"), parsed("HTTPS://Exemple.org:443/"))
        assertEquals(parsed("exemple.org").hashCode(), parsed("https://exemple.org").hashCode())
    }

    @Test
    fun twoAddressesDifferWhenTheirSubdirectoryDiffers() {
        assertTrue(parsed("exemple.org") != parsed("exemple.org/freshrss"))
    }

    @Test
    fun anAddressIsNotEqualToAnotherType() {
        assertFalse(parsed("exemple.org").equals("https://exemple.org"))
    }

    @Test
    fun toStringShowsTheNormalizedAddress() {
        // No secret to mask here: seeing the address in logs is useful.
        assertTrue("https://exemple.org" in parsed("exemple.org").toString())
    }
}
