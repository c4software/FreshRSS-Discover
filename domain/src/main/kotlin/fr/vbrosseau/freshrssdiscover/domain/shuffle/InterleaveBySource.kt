package fr.vbrosseau.freshrssdiscover.domain.shuffle

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/**
 * Portée du réordonnancement, en nombre d'articles.
 *
 * C'est **la** valeur qui définit ce que « mélanger localement » veut dire
 * (SPECS.md §4.2, règle 2). Un article ne peut être avancé que dans cette
 * fenêtre : au-delà, il n'est jamais candidat, quelle que soit la monotonie des
 * sources. Huit articles représentent environ trois écrans — assez pour trouver
 * une autre source dans la plupart des flux réels, trop peu pour qu'un
 * déplacement se lise comme une anomalie de date. Une fenêtre large
 * mélangerait mieux mais finirait par remonter l'ancien au-dessus du récent,
 * ce que la règle 2 interdit ; une fenêtre de 2 ne casserait que les doublons
 * immédiats et laisserait un flux prolifique occuper l'écran par paires.
 */
private const val LOOKAHEAD_WINDOW = 8

/**
 * Réordonne [articles] pour répartir les sources sans trahir la chronologie.
 *
 * [articles] est attendu dans l'ordre du serveur, c'est-à-dire chronologique
 * inverse ; la sortie est une **permutation exacte** de l'entrée.
 *
 * Le procédé est glouton : à chaque position, on prend le plus ancien candidat
 * *encore disponible* dont le flux diffère du précédent, en ne regardant que
 * les [LOOKAHEAD_WINDOW] premiers restants. Faute de candidat éligible dans la
 * fenêtre — tous les articles proches viennent du même flux — la tête est prise
 * telle quelle : la règle 1 cède devant la règle 2, conformément à l'ordre de
 * priorité de SPECS.md §4.2 (« tant qu'une autre source est disponible »).
 *
 * Ce que ce choix garantit sur la récence :
 * - **en avant** : un article n'est jamais présenté plus de
 *   `LOOKAHEAD_WINDOW - 1` positions avant son rang chronologique, puisqu'il
 *   n'est candidat qu'une fois entré dans la fenêtre ;
 * - **en arrière** : un article en tête n'est différé qu'**une seule fois de
 *   suite**. Le sauter impose d'émettre un article d'un autre flux, ce qui rend
 *   la tête éligible au tour suivant, où elle est le plus ancien candidat donc
 *   choisie.
 *
 * Le résultat ne dépend que de [articles] et de [previousTail] : pas d'aléa,
 * pas d'horloge, aucun parcours d'ensemble non ordonné (règle 3). Le même
 * ensemble d'articles produit donc le même ordre à chaque affichage.
 *
 * @param previousTail fin de la page précédente, pour que la règle 1 tienne
 *   aussi à la jonction entre deux pages (règle 4). Seul son **dernier**
 *   élément contraint le résultat — la monotonie ne se juge qu'entre voisins
 *   immédiats — mais l'appelant tient naturellement la fin de sa page et n'a
 *   pas à savoir combien d'éléments la règle consomme.
 */
fun interleaveBySource(
    articles: List<Article>,
    previousTail: List<Article> = emptyList(),
): List<Article> {
    val pending = articles.toMutableList()
    val ordered = ArrayList<Article>(articles.size)
    var previousFeedId = previousTail.lastOrNull()?.let { it.feed.id }
    while (pending.isNotEmpty()) {
        val chosen = pending.removeAt(nextIndex(pending, previousFeedId))
        previousFeedId = chosen.feed.id
        ordered += chosen
    }
    return ordered
}

/**
 * Position, dans [pending], de l'article à présenter ensuite.
 *
 * Repli sur `0` : conserver l'ordre du serveur est le comportement le moins
 * surprenant quand aucune alternative n'existe.
 */
private fun nextIndex(
    pending: List<Article>,
    previousFeedId: String?,
): Int {
    val window = minOf(LOOKAHEAD_WINDOW, pending.size)
    val eligible = (0 until window).firstOrNull { pending[it].feed.id != previousFeedId }
    return eligible ?: 0
}
