package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.presentation.feed.truncatedAtWord

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
     * Où aller chercher l'illustration, `null` quand l'article n'en annonce
     * aucune.
     *
     * L'URL est transportée telle que le serveur l'a fournie : la valider ou la
     * normaliser serait un calcul, et il n'appartient pas à l'affichage.
     */
    val imageUrl: String? = null,
    /**
     * Vraie lorsque l'article annonce une illustration, donc que la carte lui
     * réserve un créneau.
     *
     * Distincte de [imageUrl], qui dit seulement **où** la chercher : les deux
     * ne se dissocient que dans les tests et les prévisualisations, où l'on veut
     * la carte illustrée sans déclencher de requête. La projection ci-dessous
     * les garde toujours d'accord.
     */
    val hasIllustration: Boolean = imageUrl != null,
    /**
     * Lien d'origine, `null` quand le flux n'en a fourni aucun d'exploitable.
     *
     * Transporté tel que le serveur l'a donné : c'est `ArticleOpener` qui décide
     * ce qui est ouvrable, et il revalide de toute façon ce qu'on lui passe.
     */
    val url: String? = null,
    /**
     * Fausse lorsque l'article n'a pas de lien exploitable.
     *
     * SPECS.md §4.7 demande alors de le rendre non cliquable, et de le donner à
     * voir — ouvrir une page vide serait pire que ne rien proposer.
     *
     * Dérivée d'[url] par défaut, pour la même raison que [hasIllustration] :
     * un test ou une prévisualisation veut parfois la carte cliquable sans
     * fournir d'adresse.
     */
    val isOpenable: Boolean = url != null,
    /**
     * Vrai pour un article déjà lu, **qu'il l'ait été dans cette session ou
     * dans une précédente** (SPECS.md §4.5).
     *
     * Les deux origines comptent, et l'oubli de la seconde a été visible à
     * l'écran : projeté sans cet état, un article lu la veille arrivait du cache
     * comme neuf, et son fanion n'apparaissait qu'après une seconde de
     * visibilité — le temps que le marquage de la session le rétablisse.
     *
     * L'article marqué **reste dans la liste et à sa place** : le drapeau
     * n'existe que pour dire la vérité sur son état, jamais pour le faire
     * disparaître, ce qui déplacerait le contenu en cours de lecture.
     */
    val isRead: Boolean = false,
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
    imageUrl = imageUrl,
    url = url,
    isRead = isRead,
)

/** La coupure au mot près vit dans `truncatedAtWord`, partagée avec le Balayage. */
private fun String.toExcerpt(): String = truncatedAtWord(EXCERPT_MAX_LENGTH)
