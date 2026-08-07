package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedRef
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import java.lang.Long.parseUnsignedLong

/** Préfixe hérité de Google Reader, invariablement présent devant l'identifiant. */
private const val ITEM_ID_PREFIX = "tag:google.com,2005:reader/item/"

/** Catégorie portant l'état lu. Son absence signifie « non lu ». */
private const val READ_CATEGORY = "user/-/state/com.google/read"

private const val HEXADECIMAL = 16

/** Balises HTML, y compris leurs attributs. */
private val HTML_TAG = Regex("<[^>]*>")

/** Première image du contenu, quel que soit l'ordre de ses attributs. */
private val IMG_SOURCE = Regex("""<img[^>]*\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val WHITESPACE = Regex("\\s+")

/**
 * Convertit une réponse de `stream/contents` en page du domaine.
 *
 * Les articles dont l'identifiant est illisible sont **écartés** plutôt que de
 * faire échouer la page entière : un article aberrant ne doit pas priver
 * l'utilisateur des trente-neuf autres.
 */
internal fun StreamContentsDto.toArticlePage(): ArticlePage = ArticlePage(
    articles = items.mapNotNull(ItemDto::toArticleOrNull),
    // Le curseur est absent lorsque le flux est épuisé : c'est le seul signal
    // de fin, l'API ne renvoyant aucun compteur total.
    nextCursor = continuation?.takeIf(String::isNotBlank)?.let(::PageCursor),
)

internal fun ItemDto.toArticleOrNull(): Article? {
    val articleId = parseArticleId(id) ?: return null
    val rawSummary = summary?.content ?: content?.content.orEmpty()

    return Article(
        id = articleId,
        title = title.stripHtml(),
        url = firstUsableLink(),
        publishedAtEpochSeconds = published,
        summary = rawSummary.stripHtml(),
        imageUrl = illustrationOf(rawSummary),
        author = author?.stripHtml()?.takeIf(String::isNotBlank),
        feed = FeedRef(id = origin.streamId, title = origin.title.stripHtml()),
        isRead = READ_CATEGORY in categories,
    )
}

/**
 * Ramène l'identifiant à sa forme décimale.
 *
 * L'API l'expose en hexadécimal ici, mais en décimal dans `continuation` et
 * dans le paramètre `i` d'`edit-tag` : n'en garder qu'une base au-dessus de
 * cette couche évite que la confusion atteigne le marquage comme lu, où elle
 * échouerait sans rien signaler.
 *
 * `parseUnsignedLong` et non `toLong` : FreshRSS produit un entier 64 bits non
 * signé, et un identifiant au-delà de `Long.MAX_VALUE` ferait lever la seconde.
 *
 * ⚠️ Conséquence à ne pas perdre de vue : un identifiant dépassant
 * `Long.MAX_VALUE` est conservé sous forme de bits, donc **négatif** en Kotlin.
 * Le reformater avec `toString()` au moment d'un `edit-tag` enverrait un `-1`
 * au serveur ; c'est `java.lang.Long.toUnsignedString` qu'il faudra employer.
 * Voir TASKS.md, GOAL-008.
 */
private fun parseArticleId(raw: String): ArticleId? {
    val digits = raw.removePrefix(ITEM_ID_PREFIX).trim()
    return runCatching { ArticleId(parseUnsignedLong(digits, HEXADECIMAL)) }.getOrNull()
}

/**
 * `canonical` d'abord, `alternate` ensuite.
 *
 * Les deux portent la même valeur dans la pratique, mais `canonical` est le
 * champ que Google Reader destinait à cet usage. `null` lorsqu'aucun lien
 * n'est exploitable : SPECS.md §4.7 demande alors un article non cliquable,
 * pas l'ouverture d'une page vide.
 */
private fun ItemDto.firstUsableLink(): String? =
    (canonical + alternate).map(LinkDto::href).firstOrNull(String::isNotBlank)

/**
 * Illustration de l'article : `enclosure` d'abord, contenu ensuite.
 *
 * Tranche SPECS.md §8 question 6. L'ordre est celui de la fiabilité : une
 * `enclosure` est une illustration **déclarée** par le flux, alors qu'une
 * balise `<img>` du contenu peut aussi bien être un pixel de suivi, un logo de
 * pied de page ou un bouton de partage. On ne se rabat dessus que faute de
 * mieux — beaucoup de flux n'émettent aucune `enclosure`, et priver ces
 * articles d'illustration appauvrirait sensiblement le flux Discover.
 */
private fun ItemDto.illustrationOf(rawSummary: String): String? =
    enclosure.firstOrNull { it.isImage() }?.href?.takeIf(String::isNotBlank)
        ?: IMG_SOURCE.find(rawSummary)?.groupValues?.get(1)?.takeIf(String::isNotBlank)

/**
 * `startsWith("image")` et non `== "image/..."` : quand le flux source ne
 * précise pas de type MIME, FreshRSS se rabat sur le mot `image` seul.
 */
private fun EnclosureDto.isImage(): Boolean = type?.startsWith("image", ignoreCase = true) == true

/**
 * Réduit un fragment HTML au texte qu'il porte.
 *
 * Les champs de FreshRSS contiennent du HTML : l'afficher tel quel montrerait
 * des balises à l'utilisateur, et le laisser interpréter par un composant de
 * texte ouvrirait la porte à du contenu tiers non maîtrisé. Un extrait n'a
 * besoin que du texte.
 */
private fun String.stripHtml(): String = HTML_TAG.replace(this, " ")
    .decodeHtmlEntities()
    .replace(WHITESPACE, " ")
    .trim()

/**
 * Décode les seules entités que produisent réellement les flux RSS.
 *
 * `&amp;` est traité **en dernier** : l'inverse transformerait `&amp;lt;` en
 * `<`, c'est-à-dire réintroduirait une balise que l'on vient de neutraliser.
 */
private fun String.decodeHtmlEntities(): String = this
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
