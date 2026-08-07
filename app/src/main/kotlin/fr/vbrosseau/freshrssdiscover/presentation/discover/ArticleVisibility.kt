package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Cadence d'échantillonnage de la visibilité, en millisecondes.
 *
 * `ReadDetector` est pur : il ne se réveille pas tout seul et ne mesure une
 * durée qu'entre deux observations qu'on lui donne. Sans cadence propre, un
 * article resté dix secondes à l'écran ne serait **jamais** signalé, faute d'une
 * seconde observation après celle qui a armé le chronomètre (SPECS.md §4.5).
 *
 * 200 ms est le compromis entre deux erreurs opposées :
 *
 * - **Trop lent.** L'instant du franchissement n'est connu qu'à une période
 *   près : le seuil de 1 seconde se déclenche en réalité entre 1,0 s et
 *   1,0 s + période. À 200 ms l'erreur reste sous 20 %, ce qui est invisible à
 *   l'usage ; à 1 s, un article lu pourrait n'être signalé qu'au bout de 2 s,
 *   soit le double du seuil annoncé.
 * - **Trop rapide.** Échantillonner à la fréquence d'affichage (16 ms à 60 Hz)
 *   réveillerait la coroutine soixante fois par seconde pour parcourir les
 *   éléments visibles et allouer une table, alors que la précision gagnée ne
 *   change rien à une règle dont le seuil est la seconde. C'est de la batterie
 *   dépensée pour rien, en continu, pendant toute la lecture.
 *
 * 5 Hz suffit également à ne rien manquer d'un défilement : un article qui passe
 * à l'écran en moins de 200 ms ne peut de toute façon pas y rester une seconde.
 */
internal const val VISIBILITY_SAMPLING_PERIOD_MILLIS = 200L

/**
 * Fraction de l'article réellement affichée, entre 0 et 1.
 *
 * SPECS.md §4.5 précise que « 60 % de sa hauteur » se mesure **sur la part
 * visible de l'écran**, et non sur la hauteur propre de l'article : pris au pied
 * de la lettre, un article plus haut que la fenêtre ne pourrait jamais en
 * montrer 60 % de lui-même, et ne deviendrait donc jamais lu. La référence est
 * donc `min(hauteur de l'article, hauteur de la fenêtre)` : un article plus haut
 * que l'écran atteint 1,0 dès qu'il occupe toute la fenêtre, ce qui est
 * exactement la situation où on le lit.
 *
 * Fonction pure sur des entiers plutôt que méthode sur `LazyListLayoutInfo` :
 * c'est le seul calcul délicat de la mesure, et il doit être éprouvé sans
 * Compose ni Android.
 *
 * @param itemOffset position du haut de l'article, dans le repère de la fenêtre.
 * @param itemSize hauteur totale de l'article, y compris sa part hors écran.
 * @param viewportStart bord haut de la fenêtre, négatif quand la liste a une
 *   marge de contenu.
 * @param viewportEnd bord bas de la fenêtre.
 */
internal fun visibleFraction(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    // Un article de hauteur nulle, ou une fenêtre pas encore mesurée, n'a pas
    // de fraction définie : la division serait un NaN transmis au détecteur.
    val reference = minOf(itemSize, viewportEnd - viewportStart)
    if (reference <= 0) return 0f

    // Bornage des deux côtés : l'intersection de l'article et de la fenêtre est
    // vide — et non négative — dès que l'article est entièrement au-dessus ou
    // au-dessous.
    val top = maxOf(itemOffset, viewportStart)
    val bottom = minOf(itemOffset + itemSize, viewportEnd)
    return (bottom - top).coerceAtLeast(0).toFloat() / reference
}

/**
 * Traduit l'état de disposition de la liste en observation pour `ReadDetector`.
 *
 * Seuls les éléments dont la clé est l'identifiant d'un article sont retenus :
 * la liste porte aussi un pied de page, dont la clé est une chaîne, et le
 * compter comme article lu n'aurait aucun sens.
 */
internal fun LazyListLayoutInfo.articleVisibility(): Map<ArticleId, Float> =
    visibleItemsInfo
        .mapNotNull { item ->
            (item.key as? Long)?.let { id ->
                ArticleId(id) to visibleFraction(item.offset, item.size, viewportStartOffset, viewportEndOffset)
            }
        }
        .toMap()

/**
 * Échantillonne la visibilité tant que la coroutine vit.
 *
 * La boucle observe **avant** d'attendre : la première observation part sans
 * délai, sinon l'article affiché à l'ouverture de l'écran verrait son
 * chronomètre démarrer avec une période de retard.
 *
 * Elle ne s'arrête jamais d'elle-même : c'est son appelant qui la porte, et
 * c'est délibéré. L'arrêt est un fait de cycle de vie — l'écran passe en
 * arrière-plan — que cette fonction n'a pas les moyens de connaître.
 *
 * @param visibility relevé de la disposition, appelé à chaque échantillon plutôt
 *   que capturé une fois : c'est justement parce qu'il change sans que rien ne
 *   nous prévienne qu'on l'interroge périodiquement.
 */
internal suspend fun sampleVisibility(
    periodMillis: Long = VISIBILITY_SAMPLING_PERIOD_MILLIS,
    visibility: () -> Map<ArticleId, Float>,
    onVisibilityChanged: (Map<ArticleId, Float>) -> Unit,
): Unit = coroutineScope {
    while (isActive) {
        onVisibilityChanged(visibility())
        delay(periodMillis)
    }
}
