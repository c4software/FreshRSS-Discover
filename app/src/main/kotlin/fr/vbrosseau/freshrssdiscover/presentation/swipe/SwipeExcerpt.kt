package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.toUiModel
import fr.vbrosseau.freshrssdiscover.presentation.feed.truncatedAtWord

/**
 * Longueur maximale de l'extrait **en mode Balayage** (SPECS.md §8, question 8).
 *
 * Le mode Liste s'arrête à 240 caractères, calibrés sur les trois lignes d'une
 * carte. Le plein écran n'a pas cette contrainte, et la question restait
 * ouverte. Elle est tranchée ici à **1 400 caractères**, coupés sur une
 * frontière de mot comme en mode Liste.
 *
 * Deux mesures ont décidé du chiffre, l'une prise sur les captures de ce mode,
 * l'autre relevée sur des articles réels :
 *
 * - **Ce que l'écran tient.** Sur 411 dp, une ligne de `bodyLarge` porte
 *   environ 48 caractères pour 24 dp de hauteur. Sous une illustration 16/9, la
 *   ligne de source et le titre, il reste une vingtaine de lignes, soit près de
 *   1 100 caractères ; sans illustration, une trentaine, soit environ 1 500.
 *   1 400 remplit donc l'écran dans les deux cas, avec au plus un court
 *   déroulement — jamais dix pages de texte.
 * - **Ce que les articles font.** Le résumé médian mesure 1 324 caractères
 *   (SPECS.md §8, question 7). À 1 400, **l'article ordinaire est montré en
 *   entier** : la coupure devient l'exception, réservée aux flux qui publient
 *   des résumés démesurés, et c'est exactement l'inverse du mode Liste, où elle
 *   est la règle.
 *
 * Pourquoi pas le résumé entier, que le serveur envoie de toute façon :
 *
 * - **Ce n'est pas l'article.** SPECS.md §4.7 ouvre le lien d'origine dans le
 *   navigateur ; ce que le flux fournit est un résumé, souvent la première
 *   moitié d'un texte tronquée sans égard pour le sens. Le montrer en entier
 *   ferait passer pour une lecture complète ce qui s'arrête au milieu d'une
 *   phrase, et retirerait toute raison d'ouvrir l'article.
 * - **Le coût serait illimité.** Le maximum mesuré est de 34 777 caractères.
 *   Sans borne, Compose mesurerait ce paragraphe à chaque recomposition, pour
 *   une page que l'on quitte d'un geste. 1 400 en plafonne le coût à 4 %.
 */
const val SWIPE_EXCERPT_MAX_LENGTH = 1_400

/**
 * Projette un article du domaine dans sa forme affichable en plein écran.
 *
 * Construite **à partir de** la projection du mode Liste plutôt qu'à côté
 * d'elle : tout y est identique — titre, source, ancienneté, illustration,
 * lien — sauf la longueur de l'extrait, qui est la seule chose que le plein
 * écran change (SPECS.md §4.8).
 *
 * @param nowEpochMillis instant de référence, fourni par `Clock`.
 */
fun Article.toSwipeUiModel(nowEpochMillis: Long): ArticleUiModel =
    toUiModel(nowEpochMillis).copy(excerpt = summary.toSwipeExcerpt())

/** La coupure au mot près vit dans `truncatedAtWord`, partagée avec la Liste. */
private fun String.toSwipeExcerpt(): String = truncatedAtWord(SWIPE_EXCERPT_MAX_LENGTH)
