package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cette normalisation est le premier contact de l'utilisateur avec
 * l'application, et le plus exposé aux fautes de frappe. Elle est pure : la
 * couvrir exhaustivement ne coûte rien et évite un aller-retour réseau par cas
 * limite.
 */
class ServerAddressTest {
    private fun parsed(raw: String): ServerAddress {
        val result = ServerAddress.parse(raw)
        assertTrue(result is ServerAddressResult.Valid, "attendu valide, obtenu $result")
        return result.address
    }

    @Test
    fun aBareDomainNameGetsHttps() {
        // La saisie la plus courante. Sans schéma implicite, `URI` n'y voit
        // aucun hôte et tout échouerait dès le premier essai.
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
        // Les instances auto-hébergées sur réseau local sont un cas réel :
        // les refuser rendrait l'application inutilisable pour elles.
        val address = parsed("http://192.168.1.20:8080")

        assertEquals("http://192.168.1.20:8080", address.baseUrl)
        assertFalse(address.isSecure)
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        // Un copier-coller depuis un courriel emporte souvent une espace.
        assertEquals("https://exemple.org", parsed("  https://exemple.org \n").baseUrl)
    }

    @Test
    fun trailingSlashesAreRemoved() {
        assertEquals("https://exemple.org", parsed("https://exemple.org///").baseUrl)
    }

    @Test
    fun aSubdirectoryInstallationIsPreserved() {
        // FreshRSS s'installe couramment dans un sous-répertoire ; le perdre
        // ferait répondre 404 à tous les appels.
        assertEquals("https://exemple.org/freshrss", parsed("exemple.org/freshrss/").baseUrl)
        assertEquals(
            "https://exemple.org/freshrss/api/greader.php",
            parsed("exemple.org/freshrss/").apiEndpoint,
        )
    }

    @Test
    fun aPastedApiUrlIsAcceptedAndReducedToItsRoot() {
        // Cette URL figure dans la documentation de FreshRSS et dans la
        // configuration des autres clients : la recopier est un geste naturel.
        // La concaténer telle quelle donnerait `…/greader.php/api/greader.php`.
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
        // Un nom d'hôte est insensible à la casse ; un chemin ne l'est pas, et
        // le mettre en minuscules casserait `/FreshRSS`.
        assertEquals("https://exemple.org/FreshRSS", parsed("HTTPS://Exemple.ORG/FreshRSS").baseUrl)
    }

    @Test
    fun theDefaultPortOfTheSchemeIsDropped() {
        // Sans cela, `exemple.org` et `exemple.org:443` produiraient deux
        // adresses distinctes, donc deux sessions distinctes pour un même
        // serveur.
        assertEquals("https://exemple.org", parsed("https://exemple.org:443").baseUrl)
        assertEquals("http://exemple.org", parsed("http://exemple.org:80").baseUrl)
    }

    @Test
    fun aNonDefaultPortIsKept() {
        assertEquals("https://exemple.org:8443", parsed("https://exemple.org:8443").baseUrl)
    }

    @Test
    fun theDefaultPortOfTheOtherSchemeIsNotDropped() {
        // 80 n'est pas le port par défaut de https : le retirer changerait la
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
        // Le nommer permet d'écrire un message utile plutôt qu'« adresse
        // invalide ».
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
        // Les espaces internes et les crochets déséquilibrés font lever `URI`,
        // que `parse` rattrape plutôt que de la laisser remonter.
        assertEquals(ServerAddressResult.Malformed, ServerAddress.parse("ex emple.org"))
        assertEquals(ServerAddressResult.Malformed, ServerAddress.parse("http://[oups"))
    }

    @Test
    fun twoAddressesAreEqualWhenTheyDesignateTheSameInstance() {
        // C'est cette égalité qui décide si une session enregistrée correspond
        // au serveur saisi.
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
        // Sans secret à masquer ici : voir l'adresse en journal est utile.
        assertTrue("https://exemple.org" in parsed("exemple.org").toString())
    }
}
