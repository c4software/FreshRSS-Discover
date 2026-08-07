package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les DTO sont construits directement plutôt que décodés depuis du JSON : la
 * désérialisation est déjà éprouvée par `StreamContentsDtoTest`, et la mêler
 * ici ferait échouer ces tests pour une raison sans rapport avec le mapper.
 *
 * Chaque cas correspond à un piège constaté de l'API (docs/freshrss-api.md
 * §3.4), pas à une variation imaginée pour la forme.
 */
class ArticleMappingTest {
    /**
     * Article ordinaire, dont chaque test ne modifie que le champ qu'il éprouve
     * — ce qui rend visible, à la lecture, ce que le cas met en jeu.
     */
    private val item = ItemDto(
        id = "tag:google.com,2005:reader/item/0000000000000001",
        title = "Un titre",
        published = 1_699_999_000L,
        canonical = listOf(LinkDto("https://exemple.org/article")),
        summary = ContentDto("<p>Un extrait.</p>"),
        origin = OriginDto(streamId = "feed/12", title = "Exemple"),
        author = "Alice",
    )

    private fun page(vararg items: ItemDto, continuation: String? = null) =
        StreamContentsDto(items = items.toList(), continuation = continuation).toArticlePage()

    // ----- Identifiant -------------------------------------------------------

    @Test
    fun theHexadecimalIdentifierIsBroughtBackToItsDecimalForm() {
        // L'API expose le même entier en hexadécimal ici et en décimal dans
        // `continuation` et dans le paramètre `i` d'`edit-tag`. N'en garder
        // qu'une base évite que la confusion atteigne le marquage comme lu, où
        // elle échouerait sans rien signaler à l'utilisateur.
        val article = item.copy(id = "tag:google.com,2005:reader/item/0006587a0aa0dde5").toArticleOrNull()

        assertEquals(1_786_131_047_833_061L, assertNotNull(article).id.value)
    }

    @Test
    fun anIdentifierBeyondLongMaxValueIsStillRead() {
        // FreshRSS produit un entier 64 bits **non signé** : `toLong` lèverait
        // ici, et l'article disparaîtrait du flux sans raison visible.
        val article = item.copy(id = "tag:google.com,2005:reader/item/ffffffffffffffff").toArticleOrNull()

        assertNotNull(article)
    }

    @Test
    fun anUnreadableIdentifierDiscardsOnlyItsOwnArticle() {
        // Le point important : un article aberrant ne doit pas priver
        // l'utilisateur des trente-neuf autres de la même page.
        val articles = page(
            item.copy(id = ""),
            item.copy(id = "tag:google.com,2005:reader/item/zzz", title = "Illisible"),
            item.copy(id = "tag:google.com,2005:reader/item/0000000000000002", title = "Lisible"),
        ).articles

        assertEquals(listOf("Lisible"), articles.map { it.title })
    }

    // ----- Pagination --------------------------------------------------------

    @Test
    fun aContinuationTokenBecomesTheNextCursor() {
        val articlePage = page(item, continuation = "45219")

        assertEquals(PageCursor("45219"), articlePage.nextCursor)
        assertTrue(articlePage.hasMore)
    }

    @Test
    fun anAbsentOrEmptyContinuationMeansTheEndOfTheFeed() {
        // C'est le seul signal de fin : l'API ne renvoie aucun compteur total.
        // Une chaîne vide compte comme une absence, sans quoi la page suivante
        // serait demandée indéfiniment.
        val absent = page(item, continuation = null)
        val empty = page(item, continuation = "")

        assertNull(absent.nextCursor)
        assertFalse(absent.hasMore)
        assertNull(empty.nextCursor)
        assertFalse(empty.hasMore)
    }

    // ----- État lu -----------------------------------------------------------

    @Test
    fun theReadCategoryMakesTheArticleRead() {
        val article = item.copy(
            categories = listOf(
                "user/-/state/com.google/reading-list",
                "user/-/state/com.google/read",
            ),
        ).toArticleOrNull()

        assertTrue(assertNotNull(article).isRead)
    }

    @Test
    fun theAbsenceOfTheReadCategoryMeansUnread() {
        // `…/unread` n'est jamais émis dans ce mode : seule l'absence l'exprime.
        val article = item.copy(categories = listOf("user/-/state/com.google/reading-list")).toArticleOrNull()

        assertFalse(assertNotNull(article).isRead)
    }

    @Test
    fun plainTextUserLabelsDoNotDisturbTheReadDetection() {
        // Constaté sur une instance réelle : `categories` mêle des identifiants
        // d'état et des étiquettes utilisateur en texte nu, sans préfixe. Une
        // détection approximative les prendrait pour des états.
        val unread = item.copy(categories = listOf("AirPods Ultra", "iPhone Ultra")).toArticleOrNull()
        val read = item.copy(
            categories = listOf(
                "AirPods Ultra",
                "user/-/state/com.google/read",
                "iPhone Ultra",
            ),
        ).toArticleOrNull()

        assertFalse(assertNotNull(unread).isRead)
        assertTrue(assertNotNull(read).isRead)
    }

    // ----- Lien --------------------------------------------------------------

    @Test
    fun theCanonicalLinkWinsOverTheAlternateOne() {
        val article = item.copy(
            canonical = listOf(LinkDto("https://exemple.org/canonique")),
            alternate = listOf(LinkDto("https://exemple.org/alternatif")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/canonique", assertNotNull(article).url)
    }

    @Test
    fun anEmptyOrAbsentCanonicalFallsBackToTheAlternateLink() {
        val empty = item.copy(
            canonical = listOf(LinkDto("")),
            alternate = listOf(LinkDto("https://exemple.org/alternatif")),
        ).toArticleOrNull()
        val absent = item.copy(
            canonical = emptyList(),
            alternate = listOf(LinkDto("https://exemple.org/alternatif")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/alternatif", assertNotNull(empty).url)
        assertEquals("https://exemple.org/alternatif", assertNotNull(absent).url)
    }

    @Test
    fun anArticleWithoutAnyUsableLinkIsNotClickable() {
        // SPECS.md §4.7 demande alors un article non cliquable, pas l'ouverture
        // d'une page vide.
        val none = item.copy(canonical = emptyList(), alternate = emptyList()).toArticleOrNull()
        val blank = item.copy(canonical = listOf(LinkDto("")), alternate = listOf(LinkDto(""))).toArticleOrNull()

        assertNull(assertNotNull(none).url)
        assertNull(assertNotNull(blank).url)
    }

    // ----- Illustration ------------------------------------------------------

    @Test
    fun anImageEnclosureIsKeptAsTheIllustration() {
        val article = item.copy(
            enclosure = listOf(EnclosureDto(href = "https://exemple.org/image.jpg", type = "image/jpeg")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/image.jpg", assertNotNull(article).imageUrl)
    }

    @Test
    fun aBareImageEnclosureTypeIsAlsoAnIllustration() {
        // FreshRSS se rabat sur le mot `image` seul quand le flux source ne
        // précise aucun type MIME : exiger « image/… » raterait ces flux.
        val article = item.copy(
            enclosure = listOf(EnclosureDto(href = "https://exemple.org/i.png", type = "image")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/i.png", assertNotNull(article).imageUrl)
    }

    @Test
    fun aNonImageEnclosureIsIgnored() {
        // Un podcast joint ne doit pas se retrouver affiché comme vignette.
        val article = item.copy(
            summary = ContentDto("""<p>Texte</p><img src="https://exemple.org/vignette.jpg">"""),
            enclosure = listOf(EnclosureDto(href = "https://exemple.org/son.mp3", type = "audio/mpeg")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/vignette.jpg", assertNotNull(article).imageUrl)
    }

    @Test
    fun withoutAnyEnclosureTheFirstImageOfTheSummaryIsUsed() {
        // Cas courant, constaté sur une instance réelle : beaucoup de flux
        // n'émettent aucune `enclosure`. S'en tenir à elle appauvrirait
        // sensiblement le flux Discover.
        val article = item.copy(
            summary = ContentDto(
                """<p>Intro</p><img src="https://exemple.org/premiere.jpg"><img src="https://exemple.org/autre.jpg">""",
            ),
            enclosure = emptyList(),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/premiere.jpg", assertNotNull(article).imageUrl)
    }

    @Test
    fun anArticleWithoutAnyImageHasNoIllustration() {
        // Aucune image de remplacement : SPECS.md ne prévoit pas de vignette
        // inventée.
        val article = item.copy(summary = ContentDto("<p>Rien qu'du texte.</p>")).toArticleOrNull()

        assertNull(assertNotNull(article).imageUrl)
    }

    // ----- Nettoyage HTML ----------------------------------------------------

    @Test
    fun htmlMarkupIsReducedToItsText() {
        // Afficher le balisage tel quel montrerait des chevrons à
        // l'utilisateur ; le faire interpréter ouvrirait la porte à du contenu
        // tiers non maîtrisé.
        val article = item.copy(
            title = "<h1>Bonjour   <b>monde</b></h1>",
            summary = ContentDto("<p>Bonjour <b>monde</b></p>"),
        ).toArticleOrNull()

        assertEquals("Bonjour monde", assertNotNull(article).summary)
        assertEquals("Bonjour monde", article.title)
    }

    @Test
    fun theEntitiesProducedByRssFeedsAreDecoded() {
        val article = item.copy(
            summary = ContentDto("Tom&nbsp;&amp;&nbsp;Jerry &lt;i&gt; &quot;cite&quot; &#39;apostrophe&#39;"),
        ).toArticleOrNull()

        assertEquals("""Tom & Jerry <i> "cite" 'apostrophe'""", assertNotNull(article).summary)
    }

    @Test
    fun anEscapedAmpersandDoesNotReintroduceATag() {
        // `&amp;lt;` doit rester `&lt;` littéral : décoder `&amp;` en premier
        // réintroduirait la balise que l'on vient tout juste de neutraliser.
        val article = item.copy(summary = ContentDto("&amp;lt;script&amp;gt;")).toArticleOrNull()

        assertEquals("&lt;script&gt;", assertNotNull(article).summary)
    }

    // ----- Champs restants ---------------------------------------------------

    @Test
    fun aBlankAuthorBecomesNull() {
        // Une chaîne d'espaces afficherait une ligne d'auteur vide, plus
        // déroutante que pas de ligne du tout.
        val blank = item.copy(author = "   ").toArticleOrNull()
        val empty = item.copy(author = "").toArticleOrNull()

        assertNull(assertNotNull(blank).author)
        assertNull(assertNotNull(empty).author)
    }

    @Test
    fun theFeedIsIdentifiedByItsStreamIdAndNamedByItsTitle() {
        // Dans un flux mélangé, la source est ce qui rend l'article
        // intelligible (SPECS.md §4.3).
        val article = item.copy(origin = OriginDto(streamId = "feed/12", title = "Exemple")).toArticleOrNull()

        assertEquals("feed/12", assertNotNull(article).feed.id)
        assertEquals("Exemple", article.feed.title)
    }

    @Test
    fun aMissingSummaryFallsBackToTheContentField() {
        // `summary` est la norme de ce point d'entrée, mais un article sans
        // extrait et avec un contenu ne doit pas s'afficher vide.
        val article = item.copy(summary = null, content = ContentDto("<p>Le corps entier.</p>")).toArticleOrNull()

        assertEquals("Le corps entier.", assertNotNull(article).summary)
    }

    @Test
    fun anArticleWithNeitherSummaryNorContentHasAnEmptyExcerpt() {
        val article = item.copy(summary = null, content = null).toArticleOrNull()

        assertEquals("", assertNotNull(article).summary)
    }

    @Test
    fun thePublicationDateIsCarriedUnchangedInSeconds() {
        // Trois unités de temps coexistent dans le même objet JSON : seule
        // `published` est en secondes.
        val article = item.copy(published = 1_699_999_000L).toArticleOrNull()

        assertEquals(1_699_999_000L, assertNotNull(article).publishedAtEpochSeconds)
    }
}
