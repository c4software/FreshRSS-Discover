package fr.vbrosseau.freshrssdiscover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.SessionGate
import fr.vbrosseau.freshrssdiscover.presentation.SessionGateViewModel
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRefresh
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRefreshAction
import fr.vbrosseau.freshrssdiscover.presentation.lifecycle.ReadFlushOnBackgroundObserver
import fr.vbrosseau.freshrssdiscover.presentation.lifecycle.ReminderOnForegroundObserver
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginScreen
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginViewModel
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppDestination
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppNavHost
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppNavigationBar
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppRoutes
import fr.vbrosseau.freshrssdiscover.presentation.navigation.navigateToTopLevel
import fr.vbrosseau.freshrssdiscover.presentation.permission.StartupPermissionsRequest
import fr.vbrosseau.freshrssdiscover.presentation.recap.FeedRecap
import fr.vbrosseau.freshrssdiscover.presentation.recap.FeedRecapAction
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Registered here and nowhere else: this is the only place in the code
     * that owns a screen lifecycle. See the class for the choice of `ON_STOP`
     * and the trade-off between the `Activity` lifecycle and the process
     * lifecycle.
     */
    @Inject
    internal lateinit var readFlushOnBackground: ReadFlushOnBackgroundObserver

    /**
     * Counterpart of the above, on foreground: removes the displayed
     * reminder, records the opening time, and schedules the next day's
     * reminder (SPECS.md §4.9). See the class for the choice of `ON_START`.
     */
    @Inject
    internal lateinit var reminderOnForeground: ReminderOnForegroundObserver

    /**
     * Built with the activity, not in `onCreate`: the result contract must be
     * registered before the started state.
     */
    private val startupPermissions = StartupPermissionsRequest(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(readFlushOnBackground)
        lifecycle.addObserver(reminderOnForeground)
        // Neither awaited nor blocking: the UI mounts right after, whatever
        // the answers. See `permissionsToAskAtStartup` for what
        // `savedInstanceState` decides here.
        startupPermissions.askIfNeeded(isFirstCreation = savedInstanceState == null)
        setContent {
            AppTheme {
                /*
                 * `Surface` at the root is required: it is what installs
                 * `LocalContentColor`. Without it, any text that does not set
                 * its color falls back to black, invisible in dark theme, as
                 * happened to the login screen title rendered outside any
                 * `Scaffold`.
                 */
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

/**
 * Root switch: login screen or the signed-in app.
 *
 * Driven solely by the presence of a session. This is what returns the user
 * to the login screen when the server rejects the token (SPECS.md §3.4),
 * without any screen having to handle it.
 */
@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val viewModel: SessionGateViewModel = hiltViewModel()
    val gate by viewModel.gate.collectAsStateWithLifecycle()

    when (gate) {
        // The session lives on disk: showing the login screen during its
        // first read would make it flash on every launch.
        SessionGate.Unknown -> Box(
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }

        /*
         * `safeDrawingPadding` here, not in the signed-in `Scaffold`, which
         * already handles its insets. The login screen, rendered bare under
         * `enableEdgeToEdge`, would otherwise extend under the status bar and
         * have its title overlapped by the clock. Roborazzi captures cannot
         * catch this: they render the Composable in isolation, without system
         * bars.
         */
        SessionGate.SignedOut -> LoginRoute(modifier = modifier.safeDrawingPadding())

        SessionGate.SignedIn -> SignedInScaffold(modifier = modifier)
    }
}

@Composable
private fun LoginRoute(modifier: Modifier = Modifier) {
    val viewModel: LoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // No navigation to trigger after success: the session appears and the
    // root switch flips on its own.
    LoginScreen(
        uiState = uiState,
        onServerAddressChange = viewModel::onServerAddressChange,
        onUsernameChange = viewModel::onUsernameChange,
        onApiPasswordChange = viewModel::onApiPasswordChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

/**
 * The detail routes are not bar destinations, so they resolve no
 * `AppDestination`: their titles are named here.
 */
@StringRes
private fun detailTitleOf(route: String?): Int = when (route) {
    AppRoutes.STATS -> R.string.stats_title
    AppRoutes.SUBSCRIPTIONS -> R.string.subscriptions_title
    else -> R.string.app_name
}

/**
 * Scaffold of the signed-in app: top bar, navigation bar, nav graph.
 *
 * `TopAppBar` is still experimental in Material 3; the opt-in is local rather
 * than module-wide so the debt stays visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInScaffold(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = AppDestination.forRoute(currentRoute)

    /*
     * The refresh action is published by the displayed destination, not held
     * here: it belongs to that destination's ViewModel, which this scaffold
     * has no reason to know. `null` while no destination offers one; the bar
     * then stays bare, as on the settings screen.
     */
    var feedRefresh by remember { mutableStateOf<FeedRefresh?>(null) }

    // Same publication pattern for the recap: `null` both where no feed is
    // displayed and where the device cannot run the model (SPECS.md §4.10).
    var feedRecap by remember { mutableStateOf<FeedRecap?>(null) }

    /*
     * Same publication pattern for the tab reselection: the feed destination
     * decides what tapping its already selected tab does (scroll back to the
     * top, then reload — SPECS.md §4.6). `null` where nothing is published,
     * as on the settings screen, where the tap stays inert.
     */
    var feedReselect by remember { mutableStateOf<(() -> Unit)?>(null) }

    /*
     * Published by the immersive feed: its pages are pictures, and a bar
     * with a background would cut a band off every one of them. Both bars
     * then lose their container, the content slides under them, and the
     * page draws the scrims the title and the tabs need (SPECS.md §4.8).
     *
     * Combined with the route rather than trusted alone: the feed withdraws
     * its publication only when disposed, at the **end** of the navigation
     * transition, and the bars would start their fade once the picture had
     * already gone — a snap, seen on device (2026-08-26). The route changes
     * on `navigate`, before the transition: the fade then runs over the
     * departing page.
     */
    var feedFillsScreen by remember { mutableStateOf(false) }
    val barsAreTransparent = feedFillsScreen && currentDestination == AppDestination.DISCOVER

    Scaffold(
        // The scaffold paints nothing itself: with the bar transparent, its
        // own background would still show through as a band.
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(currentDestination?.labelRes ?: detailTitleOf(currentRoute)))
                },
                navigationIcon = {
                    // Only the pushed details go back: the two bar
                    // destinations have nowhere to go back to.
                    if (AppRoutes.isDetail(currentRoute)) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.stats_back),
                            )
                        }
                    }
                },
                // On the title row: it is a command for the whole screen, and
                // overlaid on the content it always covered part of it.
                actions = {
                    // Recap before refresh: refresh keeps its edge position,
                    // where its users' muscle memory already goes.
                    FeedRecapAction(recap = feedRecap)
                    FeedRefreshAction(refresh = feedRefresh)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = animatedTopBarContainer(transparent = barsAreTransparent),
                ),
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentRoute = currentRoute,
                onSelect = navController::navigateToTopLevel,
                onReselect = { feedReselect?.invoke() },
                transparent = barsAreTransparent,
            )
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            contentPadding = innerPadding,
            onFeedRefreshChange = { feedRefresh = it },
            onFeedRecapChange = { feedRecap = it },
            onFeedReselectChange = { feedReselect = it },
            onFeedFillsScreenChange = { feedFillsScreen = it },
        )
    }
}

/**
 * Container colour of the title bar, animated between its default and
 * nothing — like the bottom bar's: leaving the immersive feed for the
 * settings, both containers return together rather than snapping back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun animatedTopBarContainer(transparent: Boolean): Color {
    val color by animateColorAsState(
        targetValue = if (transparent) Color.Transparent else TopAppBarDefaults.topAppBarColors().containerColor,
        label = "topAppBarContainer",
    )
    return color
}
