package fr.vbrosseau.freshrssdiscover.startup

import android.os.Looper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import fr.vbrosseau.freshrssdiscover.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve le démarrage de l'activité de lancement.
 *
 * C'est le seul test qui parcourt le chemin réel : `MainActivity` est
 * `@AndroidEntryPoint`, elle réclame donc son propre composant, lequel réclame
 * à son tour le graphe applicatif entier — et, par `hiltViewModel()`, la
 * fabrique de ViewModels que rien d'autre n'exerce.
 *
 * Ce qu'il **n'établit pas** : que l'écran affiche quoi que ce soit de juste.
 * La composition n'est pas pilotée ici, aucune assertion ne porte sur le rendu.
 * Il répond à une seule question, celle qui n'avait pas de réponse : est-ce que
 * l'application démarre.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MainActivityStartupTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun theLauncherActivityReachesResumedAndComposesItsRoot() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            val activity = controller.setup().get()
            // `setContent` ne compose pas sur-le-champ : il poste sur la boucle
            // principale, que Robolectric laisse en pause. Sans cette relance,
            // aucun ViewModel ne serait demandé et le test ne prouverait que
            // l'entrée dans `onCreate`.
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(activity.isFinishing, "L'activité de lancement ne doit pas se refermer d'elle-même")
            // La racine obtient son `SessionGateViewModel` par `hiltViewModel()`
            // : un magasin non vide établit que la fabrique de ViewModels de
            // Hilt a réellement construit un ViewModel du graphe.
            assertTrue(
                activity.viewModelStore.keys().isNotEmpty(),
                "Aucun ViewModel n'a été construit : la racine n'a pas été composée",
            )
        } finally {
            // Détruire relâche le `ViewModelStore` : sans cela, l'observation
            // de session lancée par `SessionGateViewModel` survivrait au test et
            // retomberait sur un environnement démonté (voir `TestApplication`).
            controller.destroy()
        }
    }
}
