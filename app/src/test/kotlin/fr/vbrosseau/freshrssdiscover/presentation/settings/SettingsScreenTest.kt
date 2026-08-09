package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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

    /**
     * Montage propre au rappel, pour la raison qui a déjà valu le sien au mode
     * de parcours : la liste de rappels de [show] est au seuil que Detekt
     * accepte.
     */
    private fun showReminder(
        isReminderEnabled: Boolean = true,
        onReminderEnabledChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(account = account, isReminderEnabled = isReminderEnabled),
                onSignOutRequest = {},
                onSignOutConfirm = {},
                onSignOutDismiss = {},
                onVisibleFractionChange = {},
                onContinuousVisibilityChange = {},
                onReminderEnabledChange = onReminderEnabledChange,
            )
        }
    }

    /** SPECS.md §4.9 : le rappel est actif tant qu'on ne l'éteint pas. */
    @Test
    fun theReminderSwitchFollowsTheState() {
        showReminder(isReminderEnabled = true)

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).performScrollTo().assertIsOn()
    }

    @Test
    fun theReminderSwitchIsOffWhenTheStoredChoiceIsOff() {
        showReminder(isReminderEnabled = false)

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).performScrollTo().assertIsOff()
    }

    @Test
    fun turningTheReminderOffReportsIt() {
        var reported: Boolean? = null
        showReminder(isReminderEnabled = true, onReminderEnabledChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).performScrollTo().performClick()

        assertEquals(false, reported)
    }

    @Test
    fun turningTheReminderBackOnReportsIt() {
        var reported: Boolean? = null
        showReminder(isReminderEnabled = false, onReminderEnabledChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).performScrollTo().performClick()

        assertEquals(true, reported)
    }

    /**
     * L'interrupteur n'a pas de mémoire propre : il remonte le geste et attend
     * que l'état revienne, comme les segments du mode de parcours.
     */
    @Test
    fun theReminderSwitchShowsTheStateRatherThanTheLastTap() {
        showReminder(isReminderEnabled = true)

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).performScrollTo().performClick()

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).assertIsOn()
    }

    /**
     * SPECS.md §7.1 : la cible fait au moins 48 dp. Un `Switch` de Material 3
     * n'en mesure que 32 de haut — c'est la rangée entière qui porte l'action.
     */
    @Test
    fun theReminderSwitchIsBigEnoughToBeTapped() {
        showReminder()

        val bounds = composeRule.onNodeWithTag(SettingsTestTags.REMINDER)
            .performScrollTo()
            .getUnclippedBoundsInRoot()
        assertTrue(bounds.height >= 48.dp, "hauteur de la cible : ${bounds.height}")
        assertTrue(bounds.width >= 48.dp, "largeur de la cible : ${bounds.width}")
    }

    /**
     * L'heure du rappel est déduite de l'usage : ne pas la dire ferait passer
     * une notification du soir pour un caprice de l'application.
     */
    @Test
    fun theReminderExplainsWhenItGoesOff() {
        showReminder()

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER_HELP)
            .assertTextEquals(
                "Une notification quotidienne rappelle ce qu'il reste à lire, en citant quelques titres. " +
                    "Elle part à l'heure à laquelle vous avez ouvert l'application la veille, " +
                    "et rien n'est envoyé s'il ne reste rien à lire.",
            )
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
     * Un montage propre au marquage automatique, pour la raison déjà donnée à
     * [showReminder] : la liste de rappels de [show] est au seuil que Detekt
     * accepte.
     */
    private fun showAutomaticMarking(
        isAutoMarkAsReadEnabled: Boolean = true,
        onAutoMarkAsReadChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(
                    account = account,
                    isAutoMarkAsReadEnabled = isAutoMarkAsReadEnabled,
                ),
                onSignOutRequest = {},
                onSignOutConfirm = {},
                onSignOutDismiss = {},
                onVisibleFractionChange = {},
                onContinuousVisibilityChange = {},
                onAutoMarkAsReadChange = onAutoMarkAsReadChange,
            )
        }
    }

    /** SPECS.md §4.5 : le marquage a lieu tant qu'on ne l'éteint pas. */
    @Test
    fun theAutomaticMarkingSwitchFollowsTheState() {
        showAutomaticMarking(isAutoMarkAsReadEnabled = true)

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_MARK_AS_READ).performScrollTo().assertIsOn()
    }

    @Test
    fun theAutomaticMarkingSwitchIsOffWhenTheStoredChoiceIsOff() {
        showAutomaticMarking(isAutoMarkAsReadEnabled = false)

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_MARK_AS_READ).performScrollTo().assertIsOff()
    }

    @Test
    fun turningTheAutomaticMarkingOffReportsIt() {
        var reported: Boolean? = null
        showAutomaticMarking(isAutoMarkAsReadEnabled = true, onAutoMarkAsReadChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_MARK_AS_READ).performScrollTo().performClick()

        assertEquals(false, reported)
    }

    @Test
    fun turningTheAutomaticMarkingBackOnReportsIt() {
        var reported: Boolean? = null
        showAutomaticMarking(isAutoMarkAsReadEnabled = false, onAutoMarkAsReadChange = { reported = it })

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_MARK_AS_READ).performScrollTo().performClick()

        assertEquals(true, reported)
    }

    /** SPECS.md §7.1 : la rangée entière est la cible, et elle fait 48 dp. */
    @Test
    fun theAutomaticMarkingSwitchIsBigEnoughToBeTapped() {
        showAutomaticMarking()

        val bounds = composeRule.onNodeWithTag(SettingsTestTags.AUTO_MARK_AS_READ)
            .performScrollTo()
            .getUnclippedBoundsInRoot()
        assertTrue(bounds.height >= 48.dp, "hauteur de la cible : ${bounds.height}")
        assertTrue(bounds.width >= 48.dp, "largeur de la cible : ${bounds.width}")
    }

    /**
     * Ce que l'extinction ne coupe pas.
     *
     * Sans cette phrase, l'utilisateur croirait avoir suspendu tout marquage et
     * prendrait pour un défaut la disparition d'un article qu'il vient d'ouvrir
     * (SPECS.md §4.7).
     */
    @Test
    fun theAutomaticMarkingSaysWhatStaysWhenItIsOff() {
        showAutomaticMarking()

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_MARK_AS_READ_HELP)
            .assertTextEquals(
                "Sans cela, faire défiler le flux ne marque plus rien : " +
                    "les seuils ci-dessous cessent de s'appliquer. " +
                    "Ouvrir un article le marque toujours comme lu.",
            )
    }

    /**
     * Les deux seuils restent **affichés** quand le marquage est éteint : les
     * cacher ferait disparaître deux réglages sans dire pourquoi.
     */
    @Test
    fun withAutomaticMarkingOffTheThresholdsAreStillShown() {
        showAutomaticMarking(isAutoMarkAsReadEnabled = false)

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION).assertTextEquals("au moins 60 %")
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY).assertTextEquals("au moins 1 s")
    }

    /** Grisés, parce que proposer d'ajuster ce qui ne s'applique plus est un piège. */
    @Test
    fun withAutomaticMarkingOffTheThresholdSlidersAreDisabled() {
        showAutomaticMarking(isAutoMarkAsReadEnabled = false)

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION_SLIDER)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY_SLIDER)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun withAutomaticMarkingOnTheThresholdSlidersAreUsable() {
        showAutomaticMarking(isAutoMarkAsReadEnabled = true)

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION_SLIDER)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY_SLIDER)
            .performScrollTo()
            .assertIsEnabled()
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
        show(SettingsUiState(account = account, appVersion = "1.0.0"))

        composeRule.onNodeWithTag(SettingsTestTags.APP_VERSION).assertTextEquals("1.0.0")
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
