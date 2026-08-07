package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Réglages en mémoire, pour les tests.
 *
 * Il **valide comme le vrai dépôt** : un fake permissif laisserait passer un
 * appel hors bornes que l'implémentation réelle refuserait, et le test serait
 * alors vert sur un chemin impossible en production.
 */
class FakeSettingsRepository(
    initial: ReadingSettings = ReadingSettings.Default,
) : SettingsRepository {
    private val settings = MutableStateFlow(initial)

    /** Nombre d'écritures reçues, tous seuils confondus. */
    var writeCount: Int = 0
        private set

    /** Valeur courante, pour vérifier ce qui a été enregistré sans collecter. */
    val current: ReadingSettings
        get() = settings.value

    override fun observeReadingSettings(): StateFlow<ReadingSettings> = settings

    override suspend fun setVisibleFraction(value: Float) {
        writeCount++
        settings.value = settings.value.copy(visibleFraction = value)
    }

    override suspend fun setContinuousVisibilityMillis(value: Long) {
        writeCount++
        settings.value = settings.value.copy(continuousVisibilityMillis = value)
    }
}
