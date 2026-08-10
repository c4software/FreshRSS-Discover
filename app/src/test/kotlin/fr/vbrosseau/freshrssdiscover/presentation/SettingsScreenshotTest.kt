package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsAccount
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsCache
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsScreen
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsUiState
import fr.vbrosseau.freshrssdiscover.presentation.settings.continuousVisibilityThresholdOf
import fr.vbrosseau.freshrssdiscover.presentation.settings.visibleFractionThresholdOf
import org.junit.Test

/**
 * Visual references for the settings screen.
 *
 * Separate file from `ScreensScreenshotTest`: the screen has its own states,
 * including a dialog that exists nowhere else, and grouping them here keeps
 * the failure report readable.
 */
class SettingsScreenshotTest : ScreenshotTest() {

    /**
     * Thresholds at the values of SPECS.md §4.5: the duration sits on the
     * leftmost step, the position where a poorly constrained slider overflows
     * its track.
     */
    @Test
    fun settingsScreen() {
        capture("reglages") {
            settings(SettingsUiState(account = ACCOUNT, cache = CACHE, appVersion = APP_VERSION))
        }
    }

    /**
     * The cache after a purge.
     *
     * Two defects only show here: the disabled button, whose contrast depends
     * on a dimmed color the dark theme treats differently, and the purge
     * result, the only line on the screen painted in the primary color
     * (SPECS.md §7.1).
     */
    @Test
    fun settingsScreenAfterPurgingTheCache() {
        capture("reglages-cache-purge") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    cache = SettingsCache(articleCount = 428, purgeableCount = 0, lastPurgedCount = 812),
                    appVersion = APP_VERSION,
                ),
            )
        }
    }

    /**
     * Both sliders pushed to their maximum.
     *
     * The state where the track is fully filled: the active color covers the
     * whole width, and insufficient contrast between it and the background
     * shows nowhere else (SPECS.md §7.1).
     */
    @Test
    fun settingsScreenWithThresholdsAtTheirMaximum() {
        capture("reglages-seuils-maximum") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    visibleFraction = visibleFractionThresholdOf(MAXIMUM),
                    continuousVisibility = continuousVisibilityThresholdOf(MAXIMUM),
                    cache = CACHE,
                    appVersion = APP_VERSION,
                ),
            )
        }
    }

    /**
     * The sign-out confirmation over the screen.
     *
     * The state most exposed to contrast defects: the content sits under a
     * dimming scrim, and the destructive button carries its own color
     * (SPECS.md §3.5, §7.1).
     */
    @Test
    fun settingsScreenAskingToConfirmSignOut() {
        capture("reglages-deconnexion") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    cache = CACHE,
                    appVersion = APP_VERSION,
                    isSignOutConfirmationVisible = true,
                ),
            )
        }
    }

    /**
     * The Swipe mode selected.
     *
     * The default capture only shows the first segment active: the second has
     * a different shape (rounded right corner) and, once selected, a tinted
     * background the dark theme treats differently. A contrast defect between
     * the label and that background would show nowhere else (SPECS.md §7.1).
     * The description sentence also changes, and it is the longer of the two,
     * so the only one that can wrap.
     */
    @Test
    fun settingsScreenWithTheSwipePresentation() {
        capture("reglages-balayage") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    presentation = FeedPresentation.Swipe,
                    cache = CACHE,
                    appVersion = APP_VERSION,
                ),
            )
        }
    }

    /**
     * The reading reminder turned off.
     *
     * The default capture only shows the switch on, which carries the primary
     * color. Off, it relies on a dimmed track and handle whose contrast
     * depends on colors the dark theme treats differently (SPECS.md §7.1).
     */
    @Test
    fun settingsScreenWithTheReminderTurnedOff() {
        capture("reglages-rappel-eteint") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    isReminderEnabled = false,
                    cache = CACHE,
                    appVersion = APP_VERSION,
                ),
            )
        }
    }

    /**
     * Automatic marking off, so both sliders grayed out.
     *
     * The only state where a disabled track and a dimmed number sit side by
     * side: Material 3 paints them at different opacities, and the dark theme
     * treats their colors differently. A threshold left vivid above a disabled
     * track would read as still applied (SPECS.md §4.5, §7.1).
     */
    @Test
    fun settingsScreenWithTheAutomaticMarkingTurnedOff() {
        capture("reglages-marquage-eteint") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    isAutoMarkAsReadEnabled = false,
                    cache = CACHE,
                    appVersion = APP_VERSION,
                ),
            )
        }
    }

    @Composable
    private fun settings(uiState: SettingsUiState) {
        SettingsScreen(
            uiState = uiState,
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
            onVisibleFractionChange = {},
            onContinuousVisibilityChange = {},
            onPurgeCache = {},
            onPresentationChange = {},
            onReminderEnabledChange = {},
            onAutoMarkAsReadChange = {},
        )
    }

    private companion object {
        val ACCOUNT = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice")

        /** Four-digit numbers: where the thousands separator can overflow. */
        val CACHE = SettingsCache(articleCount = 1_240, purgeableCount = 812)
        val MAXIMUM = ReadingSettings(
            visibleFraction = ReadingSettings.VisibleFractionRange.endInclusive,
            continuousVisibilityMillis = ReadingSettings.ContinuousVisibilityRange.last,
        )
        const val APP_VERSION = "1.0.0"
    }
}
