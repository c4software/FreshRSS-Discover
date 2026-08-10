package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.presentation.browser.rememberArticleOpener
import fr.vbrosseau.freshrssdiscover.presentation.browser.rememberArticleSharer
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedEventToasts
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRefresh
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsScreen
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeScreen
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel

/**
 * Navigation graph.
 *
 * Each destination obtains its ViewModel here (`hiltViewModel()`) and passes
 * state to a stateless screen, keeping screens previewable and testable
 * without injection.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onFeedRefreshChange: (FeedRefresh?) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DISCOVER,
        modifier = modifier,
    ) {
        // One destination for both modes, not two routes: the feed is the
        // same, only its presentation changes (SPECS.md §4.8). Two routes
        // would force navigation on a settings change and thus a decision
        // about the back stack, for a choice that is not a move.
        composable(AppRoutes.DISCOVER) {
            val presentationViewModel: FeedPresentationViewModel = hiltViewModel()
            val presentation by presentationViewModel.presentation.collectAsStateWithLifecycle()

            when (presentation) {
                FeedPresentation.List -> DiscoverRoute(onFeedRefreshChange = onFeedRefreshChange)
                FeedPresentation.Swipe -> SwipeRoute(onFeedRefreshChange = onFeedRefreshChange)
            }
        }

        composable(AppRoutes.SETTINGS) {
            SettingsRoute()
        }
    }
}

@Composable
private fun DiscoverRoute(
    modifier: Modifier = Modifier,
    onFeedRefreshChange: (FeedRefresh?) -> Unit = {},
) {
    val viewModel: DiscoverViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The opener is built here, under `AppTheme`: it reads the bar color from
    // it, and needs the Activity `Context` so the tab stays in the app's task
    // stack; otherwise back would not return to the feed.
    val articleOpener = rememberArticleOpener()

    /*
     * Sharing does not go through the ViewModel, unlike opening: it does not
     * mark the article as read (nothing was read by sending it) and it does
     * not require the network, since nothing leaves from here; the chosen app
     * decides what to do with it. There is no upstream decision to make.
     */
    val articleSharer = rememberArticleSharer()

    AskTheServerWhenShownEmpty(viewModel::onScreenShown)

    FeedEventToasts(viewModel.events)

    PublishFeedRefresh(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        onFeedRefreshChange = onFeedRefreshChange,
        hideWhileRefreshing = true,
    )

    DiscoverScreen(
        uiState = uiState,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        // The ViewModel decides whether opening is allowed: it marks the
        // article read and refuses offline, where opening would fail without
        // any explanation.
        onArticleClick = { articleId ->
            if (viewModel.onArticleOpened(articleId)) {
                articleOpener.open(uiState.articles.firstOrNull { it.id == articleId }?.url)
            }
        },
        onArticleShare = { articleId ->
            uiState.articles.firstOrNull { it.id == articleId }?.let { article ->
                articleSharer.share(title = article.title, url = article.url)
            }
        },
        onRefresh = viewModel::refresh,
        onOfflineNoticeDismiss = viewModel::dismissOfflineOpenNotice,
        onStaleNoticeDismiss = viewModel::dismissStaleNotice,
        // Without this callback, visibility measurement does not arm: `null`
        // means nobody is listening, and automatic marking would stay inert.
        onVisibilityChanged = viewModel::onVisibilityChanged,
        modifier = modifier,
    )
}

@Composable
private fun SwipeRoute(
    modifier: Modifier = Modifier,
    onFeedRefreshChange: (FeedRefresh?) -> Unit = {},
) {
    val viewModel: SwipeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val articleOpener = rememberArticleOpener()
    val articleSharer = rememberArticleSharer()

    AskTheServerWhenShownEmpty(viewModel::onScreenShown)

    FeedEventToasts(viewModel.events)

    PublishFeedRefresh(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        onFeedRefreshChange = onFeedRefreshChange,
    )

    SwipeScreen(
        uiState = uiState,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onArticleClick = { articleId ->
            if (viewModel.onArticleOpened(articleId)) {
                articleOpener.open(uiState.articles.firstOrNull { it.id == articleId }?.url)
            }
        },
        onArticleShare = { articleId ->
            uiState.articles.firstOrNull { it.id == articleId }?.let { article ->
                articleSharer.share(title = article.title, url = article.url)
            }
        },
        onOfflineNoticeDismiss = viewModel::dismissOfflineOpenNotice,
        onRefresh = viewModel::refresh,
        onStaleNoticeDismiss = viewModel::dismissStaleNotice,
        onVisibilityChanged = viewModel::onVisibilityChanged,
        modifier = modifier,
    )
}

/**
 * Notifies the ViewModel that its screen comes to the foreground (GOAL-025).
 *
 * This fact is known here, not in the ViewModel: lifecycle is a presentation
 * concern, and a ViewModel observing it would hold a reference to something
 * with a shorter lifetime.
 *
 * `LifecycleResumeEffect`, not `LaunchedEffect`: the latter only fires on
 * entering composition, and an app returning from sleep or background does
 * not recompose; the most common case is exactly the one that would be
 * missed. `RESUMED` rather than `STARTED`, like the visibility sampling: it
 * is the only state where the screen is truly in front of the user, not
 * behind a dialog.
 *
 * What the callback decides belongs to the ViewModel: the screen reports what
 * happens, never what to do about it.
 */
@Composable
private fun AskTheServerWhenShownEmpty(onScreenShown: () -> Unit) {
    LifecycleResumeEffect(onScreenShown) {
        onScreenShown()
        onPauseOrDispose { }
    }
}

/**
 * Publishes the current destination's refresh action to the title bar.
 *
 * `DisposableEffect`, not `LaunchedEffect`: removal matters as much as
 * publication. Without `onDispose`, leaving the feed for settings would keep
 * a button wired to a ViewModel no longer on screen, and switching from List
 * to Swipe would keep the previous mode's.
 *
 * [hideWhileRefreshing] withdraws the action while a refresh runs, for
 * destinations that already display their own progress indicator; publishing
 * `null` reuses the "nothing to refresh here" path rather than adding a
 * disabled state to the button.
 */
@Composable
internal fun PublishFeedRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFeedRefreshChange: (FeedRefresh?) -> Unit,
    hideWhileRefreshing: Boolean = false,
) {
    DisposableEffect(isRefreshing, onRefresh, onFeedRefreshChange, hideWhileRefreshing) {
        val refresh = if (hideWhileRefreshing && isRefreshing) {
            null
        } else {
            FeedRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh)
        }
        onFeedRefreshChange(refresh)
        onDispose { onFeedRefreshChange(null) }
    }
}

@Composable
private fun SettingsRoute(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onSignOutRequest = viewModel::requestSignOut,
        onSignOutConfirm = viewModel::confirmSignOut,
        onSignOutDismiss = viewModel::dismissSignOut,
        onPurgeCache = viewModel::purgeCache,
        onVisibleFractionChange = viewModel::setVisibleFractionPercent,
        onContinuousVisibilityChange = viewModel::setContinuousVisibilitySeconds,
        onPresentationChange = viewModel::setFeedPresentation,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onAutoMarkAsReadChange = viewModel::setAutoMarkAsReadEnabled,
        modifier = modifier,
    )
}
