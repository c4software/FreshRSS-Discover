package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedUiState

/**
 * Ce que la vue Balayage affiche : le même état que la Liste, sous le nom que
 * ce mode a toujours employé.
 *
 * Un alias et non une classe : deux états jumeaux ont existé ici, transitions
 * comprises, et ils ont divergé — le rechargement projetait des extraits à la
 * longueur de la Liste. Voir [FeedUiState], qui documente l'état lui-même. Ce
 * que le Balayage garde en propre tient dans sa projection (`toSwipeUiModel`)
 * et dans [pageCount].
 *
 * Le rechargement de SPECS.md §4.6 y figure, mais **pas son geste** : tirer est
 * un mouvement vertical sur une liste, et en plein écran il n'y a pas de liste
 * à tirer. C'est un bouton qui le déclenche ici, et l'état à porter est le
 * même : `isRefreshing`.
 */
typealias SwipeUiState = FeedUiState

/**
 * Nombre d'écrans que le balayage traverse : les articles, **plus un**.
 *
 * Cette page supplémentaire est la traduction du pied de liste du mode Liste :
 * c'est là que la fin du flux se dit, que le chargement se voit et que l'échec
 * propose sa reprise. Sans elle, le balayage cesserait simplement de répondre
 * après le dernier article — indistinguable d'une panne (SPECS.md §4.4).
 *
 * Une dérivation propre à ce mode, déclarée chez lui : la Liste n'a pas de
 * pagineur, et porter ce compte dans l'état commun l'aurait fait mentir d'un
 * côté.
 */
val SwipeUiState.pageCount: Int get() = articles.size + 1
