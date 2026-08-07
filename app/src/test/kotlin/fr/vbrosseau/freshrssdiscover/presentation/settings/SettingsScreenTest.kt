package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La locale est figée : les nombres affichés sont mis en forme selon la
 * configuration, et une machine réglée autrement produirait « 1,240 » là où
 * l'application montre « 1 240 ».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice")

    /** Deux seuils distincts des défauts, pour que l'affichage ne puisse pas passer par hasard. */
    private val other = ReadingSettings(visibleFraction = 0.4f, continuousVisibilityMillis = 3_000L)

    private fun show(
        uiState: SettingsUiState,
        onSignOutRequest: () -> Unit = {},
        onSignOutConfirm: () -> Unit = {},
        onSignOutDismiss: () -> Unit = {},
        onVisibleFractionChange: (Int) -> Unit = {},
        onContinuousVisibilityChange: (Int) -> Unit = {},
        onPurgeCache: () -> Unit = {},
    ) {
        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onSignOutRequest = onSignOutRequest,
                onSignOutConfirm = onSignOutConfirm,
                onSignOutDismiss = onSignOutDismiss,
                onVisibleFractionChange = onVisibleFractionChange,
                onContinuousVisibilityChange = onContinuousVisibilityChange,
                onPurgeCache = onPurgeCache,
            )
        }
    }

    /**
     * Un montage propre au mode de parcours plutôt qu'un huitième paramètre à
     * [show] : la liste de rappels de l'écran a atteint le seuil que Detekt
     * accepte, et l'allonger pour deux tests rendrait les six autres illisibles.
     */
    private fun showPresentation(
        presentation: FeedPresentation = FeedPresentation.Default,
        onPresentationChange: (FeedPresentation) -> Unit = {},
    ) {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(account = account, presentation = presentation),
                onSignOutRequest = {},
                onSignOutConfirm = {},
                onSignOutDismiss = {},
                onVisibleFractionChange = {},
                onContinuousVisibilityChange = {},
                onPresentationChange = onPresentationChange,
            )
        }
    }

    @Test
    fun theListPresentationIsSelectedAndDescribedByDefault() {
        showPresentation()

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_LIST).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).assertIsNotSelected()
        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_DESCRIPTION)
            .assertTextEquals("Plusieurs articles à l'écran, que vous faites défiler vers le bas.")
    }

    /**
     * La description suit le mode sélectionné : c'est elle qui dit ce que le
     * flux montrera, ce que deux mots de segment ne peuvent pas faire.
     */
    @Test
    fun theSwipePresentationIsSelectedAndDescribedWhenItIsTheStoredOne() {
        showPresentation(presentation = FeedPresentation.Swipe)

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_LIST).assertIsNotSelected()
        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_DESCRIPTION)
            .assertTextEquals(
                "Un article à la fois en plein écran, que vous faites glisser sur le côté pour passer au suivant.",
            )
    }

    @Test
    fun choosingTheSwipePresentationReportsIt() {
        var reported: FeedPresentation? = null
        showPresentation(onPresentationChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).performScrollTo().performClick()

        assertEquals(FeedPresentation.Swipe, reported)
    }

    @Test
    fun choosingTheListPresentationBackReportsIt() {
        var reported: FeedPresentation? = null
        showPresentation(presentation = FeedPresentation.Swipe, onPresentationChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_LIST).performScrollTo().performClick()

        assertEquals(FeedPresentation.List, reported)
    }

    /**
     * Le contrôle ne mémorise rien : il remonte le choix et attend que l'état
     * revienne. Sans cela, l'écran montrerait un mode sélectionné que
     * l'enregistrement n'aurait pas retenu.
     */
    @Test
    fun theSegmentsShowTheStateRatherThanTheLastTap() {
        showPresentation()

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).performScrollTo().performClick()

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_LIST).assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).assertIsNotSelected()
    }

    @Test
    fun theConnectedServerAndUsernameAreDisplayed() {
        show(SettingsUiState(account = account))

        composeRule.onNodeWithTag(SettingsTestTags.SERVER_ADDRESS).assertTextEquals("https://rss.exemple.org")
        composeRule.onNodeWithTag(SettingsTestTags.USERNAME).assertTextEquals("alice")
    }

    @Test
    fun withoutASessionNothingIsOfferedToSignOutFrom() {
        show(SettingsUiState(account = null))

        composeRule.onNodeWithTag(SettingsTestTags.NO_SESSION).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT).assertDoesNotExist()
    }

    @Test
    fun theAutomaticReadingThresholdsAreDisplayed() {
        show(SettingsUiState(account = account))

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION).assertTextEquals("au moins 60 %")
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY).assertTextEquals("au moins 1 s")
    }

    /**
     * Le curseur est piloté par l'état, pas par une mémoire locale.
     *
     * S'il conservait sa position lui-même, l'écran continuerait d'afficher la
     * valeur relâchée même si l'enregistrement échouait — précisément le
     * mensonge que GOAL-011-T04 supprime.
     */
    @Test
    fun theDisplayedThresholdsFollowTheState() {
        show(
            SettingsUiState(
                account = account,
                visibleFraction = visibleFractionThresholdOf(other),
                continuousVisibility = continuousVisibilityThresholdOf(other),
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION).assertTextEquals("au moins 40 %")
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY).assertTextEquals("au moins 3 s")
    }

    @Test
    fun movingTheVisibleFractionSliderReportsThePercentage() {
        var reported: Int? = null
        show(SettingsUiState(account = account), onVisibleFractionChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION_SLIDER)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }

        assertEquals(100, reported)
    }

    @Test
    fun movingTheContinuousVisibilitySliderReportsTheSeconds() {
        var reported: Int? = null
        show(SettingsUiState(account = account), onContinuousVisibilityChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY_SLIDER)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }

        assertEquals(4, reported)
    }

    /**
     * Les crans bornent le geste : une valeur hors plage ferait lever le dépôt,
     * et l'utilisateur ne doit jamais pouvoir la produire.
     */
    @Test
    fun theSlidersNeverReportAValueOutsideTheAllowedRange() {
        val reported = mutableListOf<Int>()
        show(SettingsUiState(account = account), onVisibleFractionChange = reported::add)

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION_SLIDER)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }

        assertTrue(reported.all { it in SettingsUiState().visibleFraction.range }, "valeurs remontées : $reported")
    }

    /**
     * La taille du cache est un nombre d'articles, pas un poids : c'est le seul
     * chiffre qui dise ce qu'une purge retirerait (voir `CacheStatus`).
     */
    @Test
    fun theCacheSizeIsDisplayedAsACountOfArticles() {
        show(SettingsUiState(account = account, cache = SettingsCache(articleCount = 1_240, purgeableCount = 812)))

        // Le séparateur de milliers est celui de la locale du test (`fr-rFR`) :
        // l'écrire en dur figerait un caractère d'espace que la plateforme a
        // déjà changé une fois.
        val grouped = NumberFormat.getIntegerInstance(Locale.FRANCE).format(1_240)
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_SIZE).assertTextEquals("$grouped articles")
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_PURGEABLE)
            .assertTextEquals("dont 812 déjà lus et transmis au serveur")
    }

    @Test
    fun aSingleArticleIsCountedInTheSingular() {
        show(SettingsUiState(account = account, cache = SettingsCache(articleCount = 1, purgeableCount = 1)))

        composeRule.onNodeWithTag(SettingsTestTags.CACHE_SIZE).assertTextEquals("1 article")
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_PURGEABLE)
            .assertTextEquals("dont 1 déjà lu et transmis au serveur")
    }

    /** Un bouton actif qui ne supprimerait rien ferait douter de la purge. */
    @Test
    fun withNothingToPurgeTheButtonIsDisabled() {
        show(SettingsUiState(account = account, cache = SettingsCache(articleCount = 3, purgeableCount = 0)))

        composeRule.onNodeWithTag(SettingsTestTags.PURGE_CACHE).assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_PURGEABLE)
            .assertTextEquals("Aucun article lu à supprimer pour l'instant.")
    }

    /**
     * La purge part au premier appui, sans boîte de dialogue.
     *
     * Elle n'emporte que des articles lus et déjà transmis au serveur ; la
     * confirmation reste réservée à la déconnexion, qui efface le jeton, les
     * non-lus et les marquages en attente (SPECS.md §3.5).
     */
    @Test
    fun purgingAsksNothingAndReportsTheGestureImmediately() {
        var purged = 0
        show(
            SettingsUiState(account = account, cache = SettingsCache(articleCount = 9, purgeableCount = 4)),
            onPurgeCache = { purged++ },
        )

        composeRule.onNodeWithTag(SettingsTestTags.PURGE_CACHE).performScrollTo().performClick()

        assertEquals(1, purged)
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_DIALOG).assertDoesNotExist()
    }

    @Test
    fun theOutcomeOfTheLastPurgeIsDisplayed() {
        show(
            SettingsUiState(
                account = account,
                cache = SettingsCache(articleCount = 5, purgeableCount = 0, lastPurgedCount = 4),
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.CACHE_PURGE_RESULT).assertTextEquals("4 articles supprimés.")
    }

    @Test
    fun beforeAnyPurgeNoOutcomeIsDisplayed() {
        show(SettingsUiState(account = account, cache = SettingsCache(articleCount = 5, purgeableCount = 2)))

        composeRule.onNodeWithTag(SettingsTestTags.CACHE_PURGE_RESULT).assertDoesNotExist()
    }

    @Test
    fun theApplicationVersionAndLicenseAreDisplayed() {
        show(SettingsUiState(account = account, appVersion = "0.1.0"))

        composeRule.onNodeWithTag(SettingsTestTags.APP_VERSION).assertTextEquals("0.1.0")
        composeRule.onNodeWithTag(SettingsTestTags.LICENSE).assertExists()
    }

    @Test
    fun signingOutAsksForConfirmationBeforeAnythingHappens() {
        var requested = 0
        var confirmed = 0
        show(
            SettingsUiState(account = account),
            onSignOutRequest = { requested++ },
            onSignOutConfirm = { confirmed++ },
        )

        // La colonne défile : le bouton de déconnexion ferme la liste et peut
        // se trouver hors écran, où un clic ne l'atteindrait pas.
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()

        assertEquals(1, requested)
        assertEquals(0, confirmed)
    }

    @Test
    fun theConfirmationIsShownWhenTheStateAsksForIt() {
        show(SettingsUiState(account = account, isSignOutConfirmationVisible = true))

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_DIALOG).assertExists()
    }

    @Test
    fun confirmingTheDialogReportsTheSignOut() {
        var confirmed = 0
        var dismissed = 0
        show(
            SettingsUiState(account = account, isSignOutConfirmationVisible = true),
            onSignOutConfirm = { confirmed++ },
            onSignOutDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_CONFIRM).performClick()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun cancellingTheDialogReportsNoSignOut() {
        var confirmed = 0
        var dismissed = 0
        show(
            SettingsUiState(account = account, isSignOutConfirmationVisible = true),
            onSignOutConfirm = { confirmed++ },
            onSignOutDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_CANCEL).performClick()

        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }
}
