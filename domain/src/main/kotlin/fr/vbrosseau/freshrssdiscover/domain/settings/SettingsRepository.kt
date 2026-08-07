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
}
