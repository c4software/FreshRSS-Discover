package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/**
 * Longueur maximale de l'extrait, en caractères.
 *
 * Le serveur envoie le résumé complet : 1 324 caractères en médiane, 34 777 au
 * maximum mesuré (SPECS.md §8, question 7). Le laisser entier ferait mesurer à
 * Compose un paragraphe de plusieurs milliers de caractères pour n'en afficher
 * trois lignes, sur chaque carte et à chaque recomposition.
 *
 * 240 est calibré sur ce que la carte montre réellement : trois lignes de
 * `bodyMedium` sur une largeur de 411 dp tiennent environ 180 caractères, et
 * jusqu'à 210 à la plus petite taille de police système. La marge garantit que
 * la coupure visible reste celle de Compose — une ellipse en fin de troisième
 * ligne — et non un texte qui s'arrête net au milieu de la deuxième.
 */
const val EXCERPT_MAX_LENGTH = 240

/** Marque de troncature, ajoutée seulement lorsque du texte a été retiré. */
private const val ELLIPSIS = "…"

/**
 * Un article tel que la liste l'affiche.
 *
 * Tout y est déjà décidé : l'extrait est écourté, la date est réduite à une
 * ancienneté, la présence d'un lien est un booléen. Un Composable affiche cet
 * état, il ne le dérive pas (AGENTS.md §9).
 */
data class ArticleUiModel(
    /** Forme brute de l'identifiant : c'est la clé stable de la liste. */
    val id: Long,
    val title: String,
    /** Sans lui, le mélange des sources serait déroutant (SPECS.md §4.3). */
    val feedTitle: String,
    val publishedAt: RelativeTime,
    val excerpt: String,
    /**
     * Vraie lorsque l'article porte une illustration.
     *
     * L'URL n'est pas transportée : aucune bibliothèque de chargement d'images
     * n'est encore au projet, et la carte se contente de réserver la place.
     * Voir le TODO de `DiscoverScreen`.
     */
    val hasIllustration: Boolean,
    /**
     * Fausse lorsque l'article n'a pas de lien exploitable.
     *
     * SPECS.md §4.7 demande alors de le rendre non cliquable, et de le donner à
     * voir — ouvrir une page vide serait pire que ne rien proposer.
     */
    val isOpenable: Boolean,
)

/**
 * Projette un article du domaine dans sa forme affichable.
 *
 * @param nowEpochMillis instant de référence, fourni par `Clock`.
 */
fun Article.toUiModel(nowEpochMillis: Long): ArticleUiModel = ArticleUiModel(
    id = id.value,
    title = title,
    feedTitle = feed.title,
    publishedAt = relativeTimeSince(publishedAtEpochSeconds, nowEpochMillis),
    excerpt = summary.toExcerpt(),
    hasIllustration = imageUrl != null,
    isOpenable = url != null,
)

/**
 * Écourte un résumé sans couper un mot en deux.
 *
 * La coupure se fait sur la dernière espace avant la limite : une phrase
 * tranchée au milieu d'un mot se lit comme un défaut d'affichage, pas comme un
 * extrait. Un résumé sans aucune espace — jeton, URL — est coupé net, faute de
 * mieux.
 */
private fun String.toExcerpt(): String {
    if (length <= EXCERPT_MAX_LENGTH) return this

    val cut = take(EXCERPT_MAX_LENGTH)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > 0) cut.take(lastSpace) + ELLIPSIS else cut + ELLIPSIS
}
