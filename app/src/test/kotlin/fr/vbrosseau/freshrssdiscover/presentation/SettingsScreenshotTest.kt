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
 * Références visuelles de l'écran de réglages.
 *
 * Fichier distinct de `ScreensScreenshotTest` : l'écran a ses propres états —
 * dont une boîte de dialogue, qui n'existe nulle part ailleurs — et les
 * regrouper ici garde le rapport d'échec lisible.
 */
class SettingsScreenshotTest : ScreenshotTest() {

    /**
     * Les seuils aux valeurs de SPECS.md §4.5 : la durée est alors sur le cran
     * le plus à gauche, position où un curseur mal contraint déborde de sa piste.
     */
    @Test
    fun settingsScreen() {
        capture("reglages") {
            settings(SettingsUiState(account = ACCOUNT, cache = CACHE, appVersion = APP_VERSION))
        }
    }

    /**
     * Le cache vide, purge faite.
     *
     * Deux défauts ne se voient que là : le bouton **désactivé**, dont le
     * contraste dépend d'une couleur atténuée que le thème sombre traite
     * autrement, et le compte rendu de purge, seule ligne de l'écran peinte en
     * couleur primaire (SPECS.md §7.1).
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
     * Les deux curseurs poussés à leur maximum.
     *
     * C'est l'état où la piste est entièrement remplie : la couleur active
     * couvre alors toute la largeur, et un contraste insuffisant entre elle et
     * le fond ne se voit nulle part ailleurs (SPECS.md §7.1).
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
     * La confirmation de déconnexion, par-dessus l'écran.
     *
     * C'est l'état le plus exposé aux défauts de contraste : le contenu passe
     * sous un voile assombrissant, et le bouton destructeur porte une couleur
     * qui lui est propre (SPECS.md §3.5, §7.1).
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
     * Le mode Balayage sélectionné.
     *
     * La capture par défaut ne montre que le premier segment actif : le second
     * porte une **forme différente** (coin arrondi à droite) et, une fois
     * sélectionné, un fond teinté que le thème sombre traite autrement. Un
     * défaut de contraste entre le libellé et ce fond ne se verrait nulle part
     * ailleurs (SPECS.md §7.1). La phrase de description change aussi, et c'est
     * la plus longue des deux — donc la seule qui puisse passer à la ligne.
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
     * Le rappel de lecture éteint.
     *
     * La capture par défaut ne montre que la bascule allumée, qui porte la
     * couleur primaire. Éteinte, elle repose sur une piste et une poignée
     * atténuées, dont le contraste dépend de couleurs que le thème sombre
     * traite autrement (SPECS.md §7.1).
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

    @Composable
    private fun settings(uiState: SettingsUiState) {
        SettingsScreen(
            uiState = uiState,
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
            onVisibleFractionChange = {},
            onContinuousVisibilityChange = {},
        )
    }

    private companion object {
        val ACCOUNT = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice")

        /** Des chiffres à quatre positions : c'est là que le séparateur de milliers peut déborder. */
        val CACHE = SettingsCache(articleCount = 1_240, purgeableCount = 812)
        val MAXIMUM = ReadingSettings(
            visibleFraction = ReadingSettings.VisibleFractionRange.endInclusive,
            continuousVisibilityMillis = ReadingSettings.ContinuousVisibilityRange.last,
        )
        const val APP_VERSION = "1.0.0"
    }
}
