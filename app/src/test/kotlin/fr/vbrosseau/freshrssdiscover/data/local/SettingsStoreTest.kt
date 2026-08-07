package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private lateinit var dataStore: DataStore<Preferences>

    private fun store(): SettingsStore {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("reglages.preferences_pb").also(java.io.File::delete)
        }
        return SettingsStore(dataStore)
    }

    @After
    fun stopWriting() {
        scope.cancel()
    }

    @Test
    fun withoutAnythingStoredTheSpecifiedDefaultsApply() = runTest {
        // Le point qui rendait la duplication dangereuse : ce que l'écran
        // affiche à la première ouverture doit être ce que le détecteur
        // applique réellement (SPECS.md §4.5).
        assertEquals(ReadingSettings.Default, store().observeReadingSettings().first())
    }

    @Test
    fun aStoredVisibleFractionIsReadBack() = runTest {
        val store = store()

        store.setVisibleFraction(0.8f)

        assertEquals(0.8f, store.observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun aStoredContinuousVisibilityIsReadBack() = runTest {
        val store = store()

        store.setContinuousVisibilityMillis(3_000L)

        assertEquals(3_000L, store.observeReadingSettings().first().continuousVisibilityMillis)
    }

    @Test
    fun changingOneThresholdLeavesTheOtherAlone() = runTest {
        val store = store()
        store.setVisibleFraction(1.0f)

        store.setContinuousVisibilityMillis(2_000L)

        val settings = store.observeReadingSettings().first()
        assertEquals(1.0f, settings.visibleFraction)
        assertEquals(2_000L, settings.continuousVisibilityMillis)
    }

    @Test
    fun aStoredValueSurvivesAFreshStoreOnTheSameFile() = runTest {
        // C'est la raison d'être de la tâche : un réglage qui ne survit pas à
        // la fermeture est pire qu'un réglage absent. Un second `SettingsStore`
        // construit sur le même `DataStore` rejoue la relecture au démarrage.
        val store = store()
        store.setVisibleFraction(0.4f)

        assertEquals(0.4f, SettingsStore(dataStore).observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun aCorruptedVisibleFractionOnDiskIsBroughtBackWithinBounds() = runTest {
        // Écrit par une version antérieure, ou restauré d'une sauvegarde :
        // refuser de démarrer pour un réglage secondaire serait pire que
        // corriger la valeur.
        val store = store()
        dataStore.edit { it[floatPreferencesKey("reading.visible_fraction")] = 5f }

        assertEquals(1.0f, store.observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun aCorruptedContinuousVisibilityOnDiskIsBroughtBackWithinBounds() = runTest {
        val store = store()
        dataStore.edit { it[longPreferencesKey("reading.continuous_visibility_millis")] = -1L }

        assertEquals(1_000L, store.observeReadingSettings().first().continuousVisibilityMillis)
    }

    @Test
    fun aNotANumberVisibleFractionOnDiskFallsBackToTheDefault() = runTest {
        // `NaN` ne se compare à rien : sans traitement propre il traverserait
        // le bornage et rendrait le seuil inatteignable.
        val store = store()
        dataStore.edit { it[floatPreferencesKey("reading.visible_fraction")] = Float.NaN }

        assertEquals(0.6f, store.observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun writingAVisibleFractionOutOfBoundsIsRefused() = runTest {
        // Aucun contrôle de l'interface ne peut produire cette valeur : elle
        // signale un défaut de programmation, et l'enregistrer le figerait.
        assertFailsWith<IllegalArgumentException> { store().setVisibleFraction(1.5f) }
    }

    @Test
    fun writingAContinuousVisibilityOutOfBoundsIsRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { store().setContinuousVisibilityMillis(0L) }
    }

    @Test
    fun theReadingKeysDoNotCollideWithTheSessionOnes() = runTest {
        // Les deux stockages partagent le même fichier : une collision de clé
        // ferait disparaître la session au premier réglage modifié.
        val store = store()
        store.setVisibleFraction(0.4f)

        val keys = dataStore.data.first().asMap().keys.map { it.name }
        assertEquals(listOf("reading.visible_fraction"), keys)
    }

    @Test
    fun withoutAnythingStoredTheFeedIsPresentedAsAList() = runTest {
        // SPECS.md §4.8 : Liste est le mode par défaut.
        assertEquals(FeedPresentation.List, store().observeFeedPresentation().first())
    }

    @Test
    fun aStoredFeedPresentationIsReadBack() = runTest {
        val store = store()

        store.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(FeedPresentation.Swipe, store.observeFeedPresentation().first())
    }

    @Test
    fun theFeedPresentationSurvivesAFreshStoreOnTheSameFile() = runTest {
        // SPECS.md §4.8 : « l'application rouvre dans le mode que l'utilisateur
        // a quitté ». Un second store sur le même fichier rejoue ce démarrage.
        val store = store()
        store.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(FeedPresentation.Swipe, SettingsStore(dataStore).observeFeedPresentation().first())
    }

    @Test
    fun aCorruptedFeedPresentationOnDiskFallsBackToTheList() = runTest {
        // Un mode illisible ne doit pas empêcher le démarrage : le flux
        // s'ouvre en Liste, et le prochain choix réécrira la valeur.
        val store = store()
        dataStore.edit { it[stringPreferencesKey("display.feed_presentation")] = "Carrousel" }

        assertEquals(FeedPresentation.List, store.observeFeedPresentation().first())
    }

    @Test
    fun changingTheFeedPresentationLeavesTheReadingThresholdsAlone() = runTest {
        val store = store()
        store.setVisibleFraction(0.8f)

        store.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(0.8f, store.observeReadingSettings().first().visibleFraction)
        assertEquals(FeedPresentation.Swipe, store.observeFeedPresentation().first())
    }

    @Test
    fun theFeedPresentationKeyDoesNotCollideWithTheOtherOnes() = runTest {
        // Trois familles de clés partagent le fichier — `session.`, `reading.`
        // et `display.` : une collision ferait disparaître l'une au premier
        // changement d'une autre.
        val store = store()
        store.setFeedPresentation(FeedPresentation.Swipe)

        val keys = dataStore.data.first().asMap().keys.map { it.name }
        assertEquals(listOf("display.feed_presentation"), keys)
    }
}
