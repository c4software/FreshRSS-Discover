package fr.vbrosseau.freshrssdiscover.presentation.settings

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
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

    /**
     * Construit **paresseusement**, et c'est nécessaire : `stateIn` lance sa
     * coroutine de partage dès la création du ViewModel, sur `Dispatchers.Main`.
     * Un initialiseur de propriété s'exécute avant que [MainDispatcherRule] ne
     * l'ait substitué — la coroutine partirait alors sur le vrai dispatcher
     * principal, absent hors Android.
     */
    private val viewModel: SettingsViewModel by lazy { SettingsViewModel(repository, settings) }

    private fun address(raw: String): ServerAddress =
        assertIs<ServerAddressResult.Valid>(ServerAddress.parse(raw)).address

    /** L'état est publié en `WhileSubscribed` : sans abonné, il resterait figé. */
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

        // SPECS.md §4.5 : au moins 60 % de la hauteur affichée, pendant au
        // moins 1 seconde continue.
        assertEquals(60, viewModel.uiState.value.visibleFraction.value)
        assertEquals(1, viewModel.uiState.value.continuousVisibility.value)
    }

    /**
     * L'affichage vient du dépôt et non d'une copie : c'est la garantie que le
     * chiffre montré est celui que le détecteur appliquera.
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

    /**
     * Les bornes offertes par l'écran sont celles du domaine, jamais des
     * chiffres recopiés : un curseur plus large que le dépôt produirait un
     * réglage refusé au moment de l'enregistrer.
     */
    @Test
    fun theOfferedRangesAreThoseTheRepositoryAccepts() = runTest {
        observe()

        val visible = viewModel.uiState.value.visibleFraction
        val continuous = viewModel.uiState.value.continuousVisibility
        assertEquals(20..100, visible.range)
        assertEquals(1..5, continuous.range)
        // Cinq positions, donc trois crans entre les extrémités.
        assertEquals(3, visible.stepCount)
        assertEquals(3, continuous.stepCount)
    }

    @Test
    fun theStoredThresholdsSurviveANewViewModel() = runTest {
        observe()
        viewModel.setVisibleFractionPercent(80)

        // Le même dépôt, un autre ViewModel : c'est ce que fait une réouverture
        // de l'écran, et le réglage doit y survivre.
        val reopened = SettingsViewModel(repository, settings)
        keepCollecting(reopened.uiState)

        assertEquals(80, reopened.uiState.value.visibleFraction.value)
    }

    @Test
    fun theApplicationVersionIsExposed() = runTest {
        observe()

        assertTrue(viewModel.uiState.value.appVersion.isNotBlank())
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
}
