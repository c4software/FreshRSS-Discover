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
 * Exercises launcher-activity startup.
 *
 * This is the only test that walks the real path: `MainActivity` is
 * `@AndroidEntryPoint`, so it requests its own component, which in turn
 * requests the whole application graph and, through `hiltViewModel()`, the
 * ViewModel factory that nothing else exercises.
 *
 * It does not establish that the screen renders correct content: composition
 * is not driven here and no assertion covers rendering. It only answers
 * whether the application starts.
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
            // `setContent` does not compose immediately: it posts to the main
            // looper, which Robolectric leaves paused. Without this idle, no
            // ViewModel would be requested and the test would only prove that
            // `onCreate` was entered.
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(activity.isFinishing, "L'activité de lancement ne doit pas se refermer d'elle-même")
            // The root obtains its `SessionGateViewModel` via `hiltViewModel()`:
            // a non-empty store establishes that Hilt's ViewModel factory
            // actually built a ViewModel from the graph.
            assertTrue(
                activity.viewModelStore.keys().isNotEmpty(),
                "Aucun ViewModel n'a été construit : la racine n'a pas été composée",
            )
        } finally {
            // Destroying releases the `ViewModelStore`: otherwise the session
            // observation started by `SessionGateViewModel` would outlive the
            // test and hit a torn-down environment (see `TestApplication`).
            controller.destroy()
        }
    }
}
