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
 * The locale is pinned: displayed numbers are formatted per configuration, and
 * a differently configured machine would produce "1,240" where the app shows
 * "1 240".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice")

    /** Two thresholds distinct from the defaults, so the display cannot pass by accident. */
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
                onPresentationChange = {},
                onReminderEnabledChange = {},
                onAutoMarkAsReadChange = {},
            )
        }
    }

    /**
     * A dedicated setup for the presentation mode rather than an eighth
     * parameter on [show]: the screen's callback list has reached the
     * threshold Detekt accepts, and lengthening it for two tests would make
     * the six others unreadable.
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
                onPurgeCache = {},
                onPresentationChange = onPresentationChange,
                onReminderEnabledChange = {},
                onAutoMarkAsReadChange = {},
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
     * The description follows the selected mode: it is what says what the feed
     * will show, which two words on a segment cannot.
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
     * The control stores nothing: it reports the choice and waits for the
     * state to come back. Otherwise the screen would show a selected mode the
     * persistence had not retained.
     */
    @Test
    fun theSegmentsShowTheStateRatherThanTheLastTap() {
        showPresentation()

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).performScrollTo().performClick()

        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_LIST).assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.PRESENTATION_SWIPE).assertIsNotSelected()
    }

    /**
     * Dedicated setup for the reminder, for the same reason as the
     * presentation mode: the callback list of [show] is at the threshold
     * Detekt accepts.
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
                onPurgeCache = {},
                onPresentationChange = {},
                onReminderEnabledChange = onReminderEnabledChange,
                onAutoMarkAsReadChange = {},
            )
        }
    }

    /** SPECS.md §4.9: the reminder is active until turned off. */
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
     * The switch has no memory of its own: it reports the gesture and waits
     * for the state to come back, like the presentation segments.
     */
    @Test
    fun theReminderSwitchShowsTheStateRatherThanTheLastTap() {
        showReminder(isReminderEnabled = true)

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).performScrollTo().performClick()

        composeRule.onNodeWithTag(SettingsTestTags.REMINDER).assertIsOn()
    }

    /**
     * SPECS.md §7.1: the target is at least 48 dp. A Material 3 `Switch` is
     * only 32 dp tall; the whole row carries the action.
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
     * The reminder time is inferred from usage: not saying so would make an
     * evening notification look arbitrary.
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
     * The slider is driven by state, not by local memory.
     *
     * If it kept its own position, the screen would keep showing the released
     * value even when persistence failed: precisely the lie GOAL-011-T04
     * removes.
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
     * The steps bound the gesture: an out-of-range value would make the
     * repository throw, and the user must never be able to produce one.
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
     * Dedicated setup for automatic marking, for the reason already given at
     * [showReminder]: the callback list of [show] is at the threshold Detekt
     * accepts.
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
                onPurgeCache = {},
                onPresentationChange = {},
                onReminderEnabledChange = {},
                onAutoMarkAsReadChange = onAutoMarkAsReadChange,
            )
        }
    }

    /** SPECS.md §4.5: marking happens until turned off. */
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

    /** SPECS.md §7.1: the whole row is the target, and it is 48 dp. */
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
     * What turning it off does not cut.
     *
     * Without this sentence, the user would believe all marking is suspended
     * and take the disappearance of a just-opened article for a defect
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
     * Both thresholds stay displayed when marking is off: hiding them would
     * make two settings vanish without saying why.
     */
    @Test
    fun withAutomaticMarkingOffTheThresholdsAreStillShown() {
        showAutomaticMarking(isAutoMarkAsReadEnabled = false)

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION).assertTextEquals("au moins 60 %")
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY).assertTextEquals("au moins 1 s")
    }

    /** Grayed out: offering to adjust what no longer applies is a trap. */
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
     * The cache size is an article count, not a byte size: the only number
     * that says what a purge would remove (see `CacheStatus`).
     */
    @Test
    fun theCacheSizeIsDisplayedAsACountOfArticles() {
        show(SettingsUiState(account = account, cache = SettingsCache(articleCount = 1_240, purgeableCount = 812)))

        // The thousands separator comes from the test locale (`fr-rFR`):
        // hard-coding it would pin a space character the platform has already
        // changed once.
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

    /** An enabled button that would delete nothing would cast doubt on the purge. */
    @Test
    fun withNothingToPurgeTheButtonIsDisabled() {
        show(SettingsUiState(account = account, cache = SettingsCache(articleCount = 3, purgeableCount = 0)))

        composeRule.onNodeWithTag(SettingsTestTags.PURGE_CACHE).assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_PURGEABLE)
            .assertTextEquals("Aucun article lu à supprimer pour l'instant.")
    }

    /**
     * The purge fires on the first press, without a dialog.
     *
     * It only removes articles already read and reported to the server;
     * confirmation stays reserved for sign-out, which erases the token, the
     * unread articles, and the pending marks (SPECS.md §3.5).
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

        // The column scrolls: the sign-out button closes the list and may be
        // off-screen, where a click would not reach it.
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
