package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base class for visual rendering tests.
 *
 * UI tests verify what is displayed; these verify what it looks like. A
 * regression in layout, contrast, or dark theme breaks no textual assertion
 * and only shows in an image comparison.
 *
 * The screen format is pinned by `@Config(qualifiers)`: without it, the
 * reference would depend on Robolectric's default configuration, which may
 * change between versions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-rFR-w411dp-h891dp-xhdpi")
abstract class ScreenshotTest {

    /**
     * Captures a screen in both themes.
     *
     * Dark theme is not the one looked at during development, so contrast
     * defects settle there unseen. Capturing it systematically costs one image
     * and makes them visible.
     *
     * Dynamic color is disabled: it depends on the user's wallpaper, which
     * would make any reference unstable.
     *
     * @param name file name root; the theme is appended as a suffix.
     */
    @OptIn(ExperimentalTestApi::class)
    protected fun capture(name: String, content: @Composable () -> Unit) {
        listOf(THEME_LIGHT to false, THEME_DARK to true).forEach { (label, dark) ->
            runComposeUiTest {
                // The clock only advances on demand. Otherwise a progress
                // indicator, an endless animation, keeps the UI from reaching
                // idle: the capture never completes and the test spins at full
                // CPU. Observed on the home screen while syncing.
                mainClock.autoAdvance = false

                setContent {
                    AppTheme(darkTheme = dark, dynamicColor = false) {
                        // `Surface` rather than a plain `Box`: it paints the
                        // theme background (otherwise the capture would be
                        // transparent and both themes would look identical) and
                        // above all installs `LocalContentColor`. A `Box` does
                        // not: text without an explicit color fell back to
                        // black, invisible in dark theme. The capture then
                        // showed a defect the app, which renders its screens in
                        // a `Scaffold`, does not have.
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            content()
                        }
                    }
                }

                // One frame is enough to compose and measure; freezing it here
                // also makes the reference reproducible, which an animation
                // captured at an arbitrary instant is not.
                mainClock.advanceTimeByFrame()

                onRoot().captureRoboImage(filePath = "$SCREENSHOT_DIR/$name-$label.png")
            }
        }
    }

    private companion object {
        const val SCREENSHOT_DIR = "src/test/screenshots"
        const val THEME_LIGHT = "clair"
        const val THEME_DARK = "sombre"
    }
}
