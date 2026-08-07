package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Accès aux réglages persistants du marquage automatique (SPECS.md §4.5, §6).
 *
 * L'observation est un [Flow] et non une lecture ponctuelle : deux lecteurs
 * coexistent — l'écran de réglages, qui doit afficher la valeur courante, et le
 * détecteur de lecture, qui doit appliquer la nouvelle **sans redémarrage**.
 * Une lecture ponctuelle obligerait le second à relire au bon moment, c'est-à-dire
 * à deviner quand l'utilisateur a changé d'avis.
 *
 * Un écrivain par seuil plutôt qu'un `save(ReadingSettings)` : l'écran ne
 * modifie jamais les deux à la fois, et réécrire l'autre valeur au passage
 * écraserait une modification concurrente par une copie potentiellement périmée.
 */
interface SettingsRepository {
    fun observeReadingSettings(): Flow<ReadingSettings>

    /**
     * @throws IllegalArgumentException si [value] sort de
     *   [ReadingSettings.VisibleFractionRange] — un appel hors bornes vient du
     *   code, pas de l'utilisateur, et le taire figerait le défaut sur disque.
     */
    suspend fun setVisibleFraction(value: Float)

    /** @throws IllegalArgumentException si [value] sort de [ReadingSettings.ContinuousVisibilityRange]. */
    suspend fun setContinuousVisibilityMillis(value: Long)

    /**
     * Le mode de présentation du flux (SPECS.md §4.8, §6).
     *
     * Un [Flow] distinct de [observeReadingSettings] : les deux réglages n'ont
     * ni les mêmes lecteurs ni le même rythme. Les seuils n'intéressent que le
     * détecteur de lecture ; le mode décide de l'écran affiché, et le fondre
     * dans `ReadingSettings` ferait recomposer le flux entier au moindre
     * déplacement d'un curseur de marquage.
     *
     * Comme pour les seuils, l'observation vaut mieux qu'une lecture ponctuelle :
     * SPECS.md §4.8 veut que le mode s'applique **sans redémarrage**, ce qui
     * suppose que l'écran de flux apprenne le changement de lui-même.
     */
    fun observeFeedPresentation(): Flow<FeedPresentation>

    /**
     * Enregistre le mode choisi.
     *
     * Sans bornes à vérifier, contrairement aux seuils : le type énuméré rend
     * une valeur invalide impossible à construire. C'est précisément ce qu'on
     * attend de lui.
     */
    suspend fun setFeedPresentation(value: FeedPresentation)
}
