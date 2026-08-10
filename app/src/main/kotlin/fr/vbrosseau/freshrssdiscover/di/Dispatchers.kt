package fr.vbrosseau.freshrssdiscover.di

import javax.inject.Qualifier

/**
 * Qualifiers des `CoroutineDispatcher` injectables.
 *
 * Aucun composant ne référence `Dispatchers.IO` ou `Dispatchers.Default`
 * directement : sans injection, un test ne peut ni contrôler l'ordonnancement,
 * ni avancer le temps virtuellement.
 */

/** Entrées/sorties bloquantes : base de données, DataStore, appels système. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Calcul : évaluation des règles, transformations de flux. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
