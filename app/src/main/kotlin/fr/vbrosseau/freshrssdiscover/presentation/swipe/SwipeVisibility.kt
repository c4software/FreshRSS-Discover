package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlin.math.absoluteValue

/**
 * Traduit la position du balayage en observation pour `ReadDetector`
 * (SPECS.md §4.5 et §4.8, GOAL-012-T01).
 *
 * **Pourquoi une fonction propre à ce mode.** En mode Liste, la visibilité se
 * lit dans `LazyListState.layoutInfo` : autant d'articles à l'écran, chacun
 * avec sa part visible. Ici il n'y a pas de liste paresseuse et pas de
 * disposition à parcourir — il y a une position de pagination, c'est-à-dire un
 * rang et un décalage. C'est cette position, et elle seule, qui dit ce que
 * l'utilisateur a sous les yeux.
 *
 * **Pourquoi un décalage plutôt que la page « posée ».** Prendre `settledPage`
 * reviendrait à créditer l'article précédent pendant toute la durée du geste,
 * alors qu'il est déjà à moitié sorti de l'écran. Le décalage donne la vraie
 * réponse et la donne exactement comme en mode Liste : la page courante occupe
 * `1 - |décalage|` de l'écran, sa voisine le reste. Un article posé vaut donc
 * 1,0 — le seuil de surface de SPECS.md §4.5 est satisfait d'emblée, et la
 * durée décide seule, ce que §4.8 annonce. Et pendant un balayage lent, aucune
 * des deux pages ne franchit 60 % assez longtemps : le geste ne marque rien,
 * ce qui est le comportement voulu.
 *
 * Fonction pure sur des entiers et un flottant, hors de tout Composable : c'est
 * le seul calcul délicat de la mesure, et il doit être éprouvé sans Compose ni
 * Android — exactement comme `visibleFraction` en mode Liste.
 *
 * @param articleIds identifiants des articles, dans l'ordre du balayage. Le
 *   rang au-delà du dernier est la page de fin de flux : elle n'est pas un
 *   article, et rien n'y est chronométré.
 * @param currentPage rang de la page courante du pagineur.
 * @param currentPageOffsetFraction décalage de la page courante, dans
 *   `]-1, 1[` : positif vers l'article suivant, négatif vers le précédent.
 */
internal fun pagerVisibility(
    articleIds: List<Long>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): Map<ArticleId, Float> {
    val current = articleIds.getOrNull(currentPage) ?: return emptyMap()

    val offset = currentPageOffsetFraction.absoluteValue.coerceIn(0f, 1f)
    val neighbour = if (currentPageOffsetFraction > 0f) currentPage + 1 else currentPage - 1

    return buildMap {
        put(ArticleId(current), 1f - offset)
        // Le voisin n'existe qu'en cours de geste, et pas toujours : aux deux
        // bouts du flux, et vers la page de fin, il n'y a pas d'article.
        if (offset > 0f) articleIds.getOrNull(neighbour)?.let { put(ArticleId(it), offset) }
    }
}
