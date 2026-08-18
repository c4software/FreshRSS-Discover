package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.presentation.browser.rememberArticleOpener
import fr.vbrosseau.freshrssdiscover.presentation.browser.rememberArticleSharer
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedEventToasts
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRefresh
import fr.vbrosseau.freshrssdiscover.presentation.recap.FeedRecap
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheet
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapViewModel
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsScreen
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsViewModel
import fr.vbrosseau.freshrssdiscover.presentation.stats.StatsScreen
import fr.vbrosseau.freshrssdiscover.presentation.stats.StatsViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeScreen
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel
import kotlinx.coroutines.launch

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
    onFeedRecapChange: (FeedRecap?) -> Unit = {},
    onFeedReselectChange: ((() -> Unit)?) -> Unit = {},
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

            // Above the mode switch: the recap belongs to the feed, not to
            // one of its presentations, and switching modes must not re-ask
            // the platform whether the model exists.
            val recapViewModel: RecapViewModel = hiltViewModel()
            val recapUiState by recapViewModel.uiState.collectAsStateWithLifecycle()

            PublishFeedRecap(
                isModelUsable = recapUiState.isModelUsable,
                onRecap = recapViewModel::onRecapRequested,
                onFeedRecapChange = onFeedRecapChange,
            )

            // The same opener as the feed cards: a summary's detail is the
            // original article, nothing of ours.
            val recapArticleOpener = rememberArticleOpener()

            RecapSheet(
                state = recapUiState.sheet,
                onDownloadConfirm = recapViewModel::onDownloadConfirmed,
                onItemClick = recapArticleOpener::open,
                onLoadMore = recapViewModel::onLoadMore,
                onDismiss = recapViewModel::onSheetDismissed,
            )

            when (presentation) {
                FeedPresentation.List -> DiscoverRoute(
                    onFeedRefreshChange = onFeedRefreshChange,
                    onFeedReselectChange = onFeedReselectChange,
                    onDisplayedArticlesChange = recapViewModel::onDisplayedOrderChanged,
                )

                FeedPresentation.Swipe -> SwipeRoute(
                    onFeedRefreshChange = onFeedRefreshChange,
                    onFeedReselectChange = onFeedReselectChange,
                    onDisplayedArticlesChange = recapViewModel::onDisplayedOrderChanged,
                )
            }
        }

        composable(AppRoutes.SETTINGS) {
            SettingsRoute(onOpenStats = { navController.navigate(AppRoutes.STATS) })
        }

        // Below the bar's destinations: reached from the settings, left with
        // back. The bar stays visible — the screen is a detail, not a modal.
        composable(AppRoutes.STATS) {
            StatsRoute()
        }
    }
}

@Composable
private fun StatsRoute(modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(uiState = uiState, modifier = modifier)
}

@Composable
private fun DiscoverRoute(
    modifier: Modifier = Modifier,
    onFeedRefreshChange: (FeedRefresh?) -> Unit = {},
    onFeedReselectChange: ((() -> Unit)?) -> Unit = {},
    onDisplayedArticlesChange: (List<ArticleId>) -> Unit = {},
) {
    val viewModel: DiscoverViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Hoisted from the screen so the tab reselection can drive the scroll:
    // the screen's own default state would be out of this route's reach.
    val listState = rememberLazyListState()

    PublishDisplayedArticles(
        articles = uiState.articles,
        onDisplayedArticlesChange = onDisplayedArticlesChange,
        // The list's items are the articles in order, footer last, so the
        // first visible item index is the first visible article's rank.
        // Remembered so the effect keys on a stable lambda, not one rebuilt
        // each recomposition.
        firstDisplayedIndex = remember(listState) { { listState.firstVisibleItemIndex } },
    )

    PublishFeedReselect(
        onFeedReselectChange = onFeedReselectChange,
        onReselect = rememberScrollToTopThenRefresh(
            listState = listState,
            onRefresh = viewModel::refresh,
        ),
    )

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
        // The pull indicator already animates: the bar button stays put,
        // disabled, instead of doubling the spinner or vanishing.
        showsProgress = false,
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
        listState = listState,
    )
}

@Composable
private fun SwipeRoute(
    modifier: Modifier = Modifier,
    onFeedRefreshChange: (FeedRefresh?) -> Unit = {},
    onFeedReselectChange: ((() -> Unit)?) -> Unit = {},
    onDisplayedArticlesChange: (List<ArticleId>) -> Unit = {},
) {
    val viewModel: SwipeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PublishDisplayedArticles(uiState.articles, onDisplayedArticlesChange)
    val articleOpener = rememberArticleOpener()
    val articleSharer = rememberArticleSharer()

    // No list to scroll back in this mode: reselecting the tab goes straight
    // to the reload, which already restarts the deck from the top
    // (SPECS.md §4.6).
    PublishFeedReselect(
        onFeedReselectChange = onFeedReselectChange,
        onReselect = viewModel::refresh,
    )

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
 * [showsProgress] is `false` for destinations that already display their own
 * progress indicator: the button then stays published but disabled during
 * the refresh — withdrawing it used to shift the recap button beside it
 * (GOAL-037-T14).
 */
@Composable
internal fun PublishFeedRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFeedRefreshChange: (FeedRefresh?) -> Unit,
    showsProgress: Boolean = true,
) {
    DisposableEffect(isRefreshing, onRefresh, onFeedRefreshChange, showsProgress) {
        onFeedRefreshChange(
            FeedRefresh(isRefreshing = isRefreshing, showsProgress = showsProgress, onRefresh = onRefresh),
        )
        onDispose { onFeedRefreshChange(null) }
    }
}

/**
 * Publishes the on-screen article order to whoever asked (the recap): the
 * summaries must follow the list as the user sees it, and only the displayed
 * mode knows that order. Keyed on the ids, not the models: read-state or
 * illustration changes must not republish an unchanged order.
 *
 * The order starts at the first article actually on screen (author's ruling,
 * 2026-08-18): what was scrolled past is behind the reader, and a recap
 * opening on it would retell a part of the feed already left. [firstDisplayedIndex]
 * is a snapshot read, not a value: the scroll moves without recomposing this
 * publisher, so the effect observes it through `snapshotFlow`. The default
 * serves the swipe mode, whose deck always shows its articles from the top.
 */
@Composable
internal fun PublishDisplayedArticles(
    articles: List<ArticleUiModel>,
    onDisplayedArticlesChange: (List<ArticleId>) -> Unit,
    firstDisplayedIndex: () -> Int = { 0 },
) {
    val ids = articles.map { ArticleId(it.id) }
    LaunchedEffect(ids, onDisplayedArticlesChange, firstDisplayedIndex) {
        snapshotFlow { firstDisplayedIndex().coerceAtLeast(0) }
            .collect { first -> onDisplayedArticlesChange(ids.drop(first)) }
    }
}

/**
 * Publishes the recap action to the title bar, mirror of [PublishFeedRefresh].
 *
 * The `null` publication carries the dynamic rule (SPECS.md §4.10): while the
 * platform has not answered, or answered that the model cannot run here, the
 * bar shows nothing — the feature does not exist on this device rather than
 * being disabled.
 */
@Composable
internal fun PublishFeedRecap(
    isModelUsable: Boolean,
    onRecap: () -> Unit,
    onFeedRecapChange: (FeedRecap?) -> Unit,
) {
    DisposableEffect(isModelUsable, onRecap, onFeedRecapChange) {
        onFeedRecapChange(if (isModelUsable) FeedRecap(onRecap = onRecap) else null)
        onDispose { onFeedRecapChange(null) }
    }
}

/**
 * Publishes what tapping the already selected Discover tab should do
 * (SPECS.md §4.6).
 *
 * Same shape as [PublishFeedRefresh], for the same reason: the navigation bar
 * belongs to the scaffold while the reaction belongs to the displayed
 * destination, and `onDispose` withdraws the callback so leaving the feed
 * never leaves the bar wired to a ViewModel no longer on screen.
 */
@Composable
internal fun PublishFeedReselect(
    onFeedReselectChange: ((() -> Unit)?) -> Unit,
    onReselect: () -> Unit,
) {
    DisposableEffect(onFeedReselectChange, onReselect) {
        onFeedReselectChange(onReselect)
        onDispose { onFeedReselectChange(null) }
    }
}

/**
 * The List-mode reaction to a tab reselection: back to the top, then reload.
 *
 * Inert when the list already sits at the top (SPECS.md §4.6): there is
 * nowhere to bring the reader back to, and a reload there would empty a feed
 * they did not ask to lose — the pull gesture and the title-bar button remain
 * the deliberate ways to reload from the top.
 *
 * Sequenced, not simultaneous: `animateScrollToItem` suspends until the list
 * has settled at the top, so the reload — which replaces the content and
 * snaps back to the first article (SPECS.md §4.6) — never races the
 * animation it would otherwise interrupt.
 *
 * Remembered so [PublishFeedReselect] keys on a stable callback: a fresh
 * lambda each recomposition would republish on every frame.
 */
@Composable
internal fun rememberScrollToTopThenRefresh(
    listState: LazyListState,
    onRefresh: () -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()

    return remember<() -> Unit>(scope, listState, onRefresh) {
        {
            val isAtTop = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0

            if (!isAtTop) {
                scope.launch {
                    listState.animateScrollToItem(0)
                    onRefresh()
                }
            }
        }
    }
}

@Composable
private fun SettingsRoute(onOpenStats: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        onOpenStats = onOpenStats,
        uiState = uiState,
        onSignOutRequest = viewModel::requestSignOut,
        onSignOutConfirm = viewModel::confirmSignOut,
        onSignOutDismiss = viewModel::dismissSignOut,
        onPurgeCache = viewModel::purgeCache,
        onVisibleFractionChange = viewModel::setVisibleFractionPercent,
        onContinuousVisibilityChange = viewModel::setContinuousVisibilityMillis,
        onPresentationChange = viewModel::setFeedPresentation,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onReminderTimeChange = viewModel::setReminderTime,
        onAutoMarkAsReadChange = viewModel::setAutoMarkAsReadEnabled,
        modifier = modifier,
    )
}
