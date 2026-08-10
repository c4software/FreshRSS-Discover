package fr.vbrosseau.freshrssdiscover.presentation.settings

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeCacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.keepCollecting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessions = MutableStateFlow<AuthSession?>(null)
    private val repository = FakeAuthRepository(sessions)
    private val settings = FakeSettingsRepository()
    private val cache = FakeCacheRepository(CacheStatus(articleCount = 12, purgeableCount = 5))
    private val scheduler = FakeReminderScheduler()

    /**
     * Built lazily, and necessarily so: `stateIn` starts its sharing coroutine
     * on ViewModel creation, on `Dispatchers.Main`. A property initializer
     * runs before [MainDispatcherRule] has substituted it, so the coroutine
     * would start on the real main dispatcher, absent outside Android.
     */
    private val viewModel: SettingsViewModel by lazy { SettingsViewModel(repository, settings, cache, scheduler) }

    private fun address(raw: String): ServerAddress =
        assertIs<ServerAddressResult.Valid>(ServerAddress.parse(raw)).address

    /** The state is published with `WhileSubscribed`: without a subscriber it would stay frozen. */
    private fun TestScope.observe() = keepCollecting(viewModel.uiState)

    @Test
    fun theConnectedServerAndUsernameAreExposed() = runTest {
        sessions.value = repository.sessionOf(address("rss.exemple.org"), username = "alice")
        observe()

        val account = viewModel.uiState.value.account
        assertEquals("https://rss.exemple.org", account?.serverAddress)
        assertEquals("alice", account?.username)
    }

    @Test
    fun anAbsentSessionLeavesNoAccountToDisplay() = runTest {
        observe()

        assertNull(viewModel.uiState.value.account)
    }

    @Test
    fun theAutomaticReadingThresholdsAreExposedInTheirDisplayedUnits() = runTest {
        observe()

        // SPECS.md §4.5: at least 60% of the displayed height, for at least
        // 1 continuous second.
        assertEquals(60, viewModel.uiState.value.visibleFraction.value)
        assertEquals(1, viewModel.uiState.value.continuousVisibility.value)
    }

    /**
     * The display comes from the repository, not a copy: the guarantee that
     * the number shown is the one the detector will apply.
     */
    @Test
    fun theDisplayedThresholdsAreThoseStored() = runTest {
        settings.setVisibleFraction(0.8f)
        settings.setContinuousVisibilityMillis(4_000L)
        observe()

        assertEquals(80, viewModel.uiState.value.visibleFraction.value)
        assertEquals(4, viewModel.uiState.value.continuousVisibility.value)
    }

    @Test
    fun changingTheVisibleFractionStoresItInTheDomainUnit() = runTest {
        observe()

        viewModel.setVisibleFractionPercent(40)

        assertEquals(0.4f, settings.current.visibleFraction)
        assertEquals(40, viewModel.uiState.value.visibleFraction.value)
    }

    @Test
    fun changingTheContinuousVisibilityStoresItInMilliseconds() = runTest {
        observe()

        viewModel.setContinuousVisibilitySeconds(3)

        assertEquals(3_000L, settings.current.continuousVisibilityMillis)
        assertEquals(3, viewModel.uiState.value.continuousVisibility.value)
    }

    @Test
    fun changingOneThresholdLeavesTheOtherStored() = runTest {
        observe()

        viewModel.setVisibleFractionPercent(100)

        assertEquals(ReadingSettings.Default.continuousVisibilityMillis, settings.current.continuousVisibilityMillis)
    }

    @Test
    fun theAutomaticMarkingIsAnnouncedOnUntilItIsTurnedOff() = runTest {
        observe()

        assertTrue(viewModel.uiState.value.isAutoMarkAsReadEnabled)
    }

    @Test
    fun theDisplayedAutomaticMarkingIsTheStoredOne() = runTest {
        settings.setAutoMarkAsReadEnabled(false)

        observe()

        assertFalse(viewModel.uiState.value.isAutoMarkAsReadEnabled)
    }

    @Test
    fun turningTheAutomaticMarkingOffStoresItAndRepublishesIt() = runTest {
        observe()

        viewModel.setAutoMarkAsReadEnabled(false)

        assertFalse(settings.current.autoMarkAsReadEnabled)
        assertFalse(viewModel.uiState.value.isAutoMarkAsReadEnabled)
    }

    @Test
    fun turningTheAutomaticMarkingBackOnStoresItToo() = runTest {
        settings.setAutoMarkAsReadEnabled(false)
        observe()

        viewModel.setAutoMarkAsReadEnabled(true)

        assertTrue(settings.current.autoMarkAsReadEnabled)
        assertTrue(viewModel.uiState.value.isAutoMarkAsReadEnabled)
    }

    @Test
    fun turningTheAutomaticMarkingOffLeavesTheThresholdsStored() = runTest {
        observe()
        viewModel.setVisibleFractionPercent(80)

        viewModel.setAutoMarkAsReadEnabled(false)

        // Grayed out, not forgotten: they must return as-is when re-enabled,
        // and the screen keeps displaying them.
        assertEquals(0.8f, settings.current.visibleFraction)
        assertEquals(80, viewModel.uiState.value.visibleFraction.value)
    }

    /**
     * The ranges offered by the screen are those of the domain, never copied
     * numbers: a slider wider than the repository would produce a setting
     * rejected at save time.
     */
    @Test
    fun theOfferedRangesAreThoseTheRepositoryAccepts() = runTest {
        observe()

        val visible = viewModel.uiState.value.visibleFraction
        val continuous = viewModel.uiState.value.continuousVisibility
        assertEquals(20..100, visible.range)
        assertEquals(1..5, continuous.range)
        // Five positions, so three steps between the endpoints.
        assertEquals(3, visible.stepCount)
        assertEquals(3, continuous.stepCount)
    }

    @Test
    fun theStoredThresholdsSurviveANewViewModel() = runTest {
        observe()
        viewModel.setVisibleFractionPercent(80)

        // Same repository, another ViewModel: what reopening the screen does,
        // and the setting must survive it.
        val reopened = SettingsViewModel(repository, settings, cache, scheduler)
        keepCollecting(reopened.uiState)

        assertEquals(80, reopened.uiState.value.visibleFraction.value)
    }

    @Test
    fun theApplicationVersionIsExposed() = runTest {
        observe()

        assertTrue(viewModel.uiState.value.appVersion.isNotBlank())
    }

    @Test
    fun theCacheContentIsExposedAsACountOfArticles() = runTest {
        observe()

        assertEquals(12, viewModel.uiState.value.cache.articleCount)
        assertEquals(5, viewModel.uiState.value.cache.purgeableCount)
    }

    @Test
    fun nothingHasBeenPurgedBeforeTheFirstPurge() = runTest {
        observe()

        assertNull(viewModel.uiState.value.cache.lastPurgedCount)
    }

    /**
     * The purge fires without confirmation: unlike sign-out, it only removes
     * read articles already reported to the server.
     */
    @Test
    fun purgingRemovesTheReadArticlesRightAway() = runTest {
        observe()

        viewModel.purgeCache()

        assertEquals(1, cache.purgeCount)
        assertEquals(7, viewModel.uiState.value.cache.articleCount)
        assertEquals(0, viewModel.uiState.value.cache.purgeableCount)
    }

    @Test
    fun purgingReportsHowManyArticlesWereRemoved() = runTest {
        observe()

        viewModel.purgeCache()

        // The result report replaces a prior confirmation: it is the only
        // feedback the user gets from the press.
        assertEquals(5, viewModel.uiState.value.cache.lastPurgedCount)
    }

    @Test
    fun purgingAnAlreadyPurgedCacheReportsZeroRatherThanNothing() = runTest {
        observe()
        viewModel.purgeCache()

        viewModel.purgeCache()

        assertEquals(0, viewModel.uiState.value.cache.lastPurgedCount)
    }

    @Test
    fun requestingSignOutOnlyAsksForConfirmation() = runTest {
        sessions.value = repository.sessionOf(address("rss.exemple.org"))
        observe()

        viewModel.requestSignOut()

        assertTrue(viewModel.uiState.value.isSignOutConfirmationVisible)
        assertEquals(0, repository.signOutCallCount)
    }

    @Test
    fun confirmingSignOutClearsTheSession() = runTest {
        sessions.value = repository.sessionOf(address("rss.exemple.org"))
        observe()

        viewModel.requestSignOut()
        viewModel.confirmSignOut()

        assertEquals(1, repository.signOutCallCount)
        assertFalse(viewModel.uiState.value.isSignOutConfirmationVisible)
        assertNull(viewModel.uiState.value.account)
    }

    @Test
    fun dismissingTheConfirmationKeepsTheSession() = runTest {
        sessions.value = repository.sessionOf(address("rss.exemple.org"))
        observe()

        viewModel.requestSignOut()
        viewModel.dismissSignOut()

        assertEquals(0, repository.signOutCallCount)
        assertFalse(viewModel.uiState.value.isSignOutConfirmationVisible)
        assertEquals("alice", viewModel.uiState.value.account?.username)
    }

    @Test
    fun theFeedIsPresentedAsAListUntilSomethingElseIsChosen() = runTest {
        observe()

        // SPECS.md §4.8: List is the default mode.
        assertEquals(FeedPresentation.List, viewModel.uiState.value.presentation)
    }

    @Test
    fun theDisplayedPresentationIsTheStoredOne() = runTest {
        settings.setFeedPresentation(FeedPresentation.Swipe)
        observe()

        assertEquals(FeedPresentation.Swipe, viewModel.uiState.value.presentation)
    }

    /**
     * The change goes through the repository and comes back via the published
     * state.
     *
     * What makes SPECS.md §4.8 tenable without a restart: the feed screen
     * observes the same source and learns the new mode by the same path,
     * without the settings ViewModel having to notify it.
     */
    @Test
    fun choosingTheSwipePresentationStoresItAndRepublishesIt() = runTest {
        observe()

        viewModel.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(FeedPresentation.Swipe, settings.currentPresentation)
        assertEquals(FeedPresentation.Swipe, viewModel.uiState.value.presentation)
    }

    @Test
    fun comingBackToTheListPresentationIsStoredToo() = runTest {
        settings.setFeedPresentation(FeedPresentation.Swipe)
        observe()

        viewModel.setFeedPresentation(FeedPresentation.List)

        assertEquals(FeedPresentation.List, settings.currentPresentation)
        assertEquals(FeedPresentation.List, viewModel.uiState.value.presentation)
    }

    @Test
    fun changingThePresentationLeavesTheReadingThresholdsAlone() = runTest {
        observe()
        viewModel.setVisibleFractionPercent(80)

        viewModel.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(80, viewModel.uiState.value.visibleFraction.value)
        assertEquals(0.8f, settings.current.visibleFraction)
    }

    /** SPECS.md §4.9: whoever granted the permission already said yes once. */
    @Test
    fun theReadingReminderIsOnUntilItIsTurnedOff() = runTest {
        observe()

        assertTrue(viewModel.uiState.value.isReminderEnabled)
    }

    @Test
    fun theDisplayedReminderStateIsTheStoredOne() = runTest {
        settings.setReminderEnabled(false)
        observe()

        assertFalse(viewModel.uiState.value.isReminderEnabled)
    }

    /**
     * The choice goes through the repository and comes back via the published
     * state: the switch never shows anything but what is stored.
     */
    @Test
    fun turningTheReminderOffStoresItAndRepublishesIt() = runTest {
        observe()

        viewModel.setReminderEnabled(false)

        assertFalse(settings.reminderEnabled.value)
        assertFalse(viewModel.uiState.value.isReminderEnabled)
    }

    @Test
    fun turningTheReminderBackOnIsStoredToo() = runTest {
        settings.setReminderEnabled(false)
        observe()

        viewModel.setReminderEnabled(true)

        assertTrue(settings.reminderEnabled.value)
        assertTrue(viewModel.uiState.value.isReminderEnabled)
    }

    /**
     * A setting that acted on nothing would be a defect: the next day's work
     * would stay armed and the reminder would fire anyway.
     */
    @Test
    fun turningTheReminderOffCancelsThePendingOne() = runTest {
        observe()

        viewModel.setReminderEnabled(false)

        assertEquals(1, scheduler.cancelCount)
        assertEquals(0, scheduler.scheduleCount)
    }

    @Test
    fun turningTheReminderOnSchedulesTheNextOne() = runTest {
        settings.setReminderEnabled(false)
        observe()

        viewModel.setReminderEnabled(true)

        assertEquals(1, scheduler.scheduleCount)
        assertEquals(0, scheduler.cancelCount)
    }

    /** Opening the screen neither schedules nor cancels: only the gesture decides. */
    @Test
    fun merelyDisplayingTheSettingsTouchesNoPendingReminder() = runTest {
        observe()

        assertEquals(0, scheduler.scheduleCount)
        assertEquals(0, scheduler.cancelCount)
    }

    @Test
    fun changingTheReminderLeavesTheOtherSettingsAlone() = runTest {
        observe()
        viewModel.setVisibleFractionPercent(80)
        viewModel.setFeedPresentation(FeedPresentation.Swipe)

        viewModel.setReminderEnabled(false)

        assertEquals(80, viewModel.uiState.value.visibleFraction.value)
        assertEquals(FeedPresentation.Swipe, viewModel.uiState.value.presentation)
    }

    @Test
    fun theStoredReminderChoiceSurvivesANewViewModel() = runTest {
        observe()
        viewModel.setReminderEnabled(false)

        val reopened = SettingsViewModel(repository, settings, cache, scheduler)
        keepCollecting(reopened.uiState)

        assertFalse(reopened.uiState.value.isReminderEnabled)
    }

    @Test
    fun theStoredPresentationSurvivesANewViewModel() = runTest {
        observe()
        viewModel.setFeedPresentation(FeedPresentation.Swipe)

        // SPECS.md §4.8: the app reopens in the mode the user left.
        val reopened = SettingsViewModel(repository, settings, cache, scheduler)
        keepCollecting(reopened.uiState)

        assertEquals(FeedPresentation.Swipe, reopened.uiState.value.presentation)
    }
}
