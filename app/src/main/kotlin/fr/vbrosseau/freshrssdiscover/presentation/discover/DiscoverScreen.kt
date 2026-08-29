package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.feed.AfterRefreshSettles
import fr.vbrosseau.freshrssdiscover.presentation.feed.ArticleIllustration
import fr.vbrosseau.freshrssdiscover.presentation.feed.ArticleShareButton
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedCentered
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedEmptyMessage
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedEndOfFeedMessage
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedFailureOrRetry
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedNotice
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedOfflineBanner
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedStaleNotice
import fr.vbrosseau.freshrssdiscover.presentation.feed.PrefetchNextPage
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * Number of remaining articles below which the next page is requested.
 *
 * SPECS.md §4.4 requires uninterrupted scrolling: waiting for the last
 * visible item would show the indicator every time. Five, an eighth of a
 * 40-item page, leaves time for a network round trip at ordinary scrolling
 * pace.
 */
private const val PREFETCH_DISTANCE = 5

/** Minimum touch target (SPECS.md §7.1): Material stops at 40 dp. */
private val MinTouchTarget = 48.dp

/**
 * The Discover feed.
 *
 * Holds no business state: it displays [uiState] and forwards gestures, which
 * makes it previewable and testable without an injection graph (AGENTS.md
 * §9). The only state it owns is the scroll position.
 *
 * @param onArticleShare no default value, unlike the notices and the refresh:
 *   an implicit `{}` would leave a visible but inert button on every card
 *   with nothing signalling it. Forgetting it must be a compile error.
 * @param onVisibilityChanged recipient of the visibility readings
 *   (SPECS.md §4.5). Nullable, null by default: the observation is a periodic
 *   loop, and arming it without a recipient would run a timer only to discard
 *   its result, wasting battery and keeping previews and rendering tests
 *   perpetually busy. `null` means "nobody is listening", which a default
 *   `{}` cannot express.
 */
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onOfflineNoticeDismiss: () -> Unit = {},
    onStaleNoticeDismiss: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    ScrollToTopAfterRefresh(listState = listState, isRefreshing = uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Above the feed, not over it: the banner informs without hiding
            // anything that remains readable (SPECS.md §5.2).
            if (uiState.showsOfflineBanner) OfflineBanner()

            FeedBody(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onRefresh = onRefresh,
                onArticleClick = onArticleClick,
                onArticleShare = onArticleShare,
                modifier = Modifier.weight(1f),
                listState = listState,
                onVisibilityChanged = onVisibilityChanged,
            )

            /*
             * Below the feed, not over it, as in immersive mode: this notice
             * lasts until dismissed or refreshed, and a notice that settles
             * takes its place in the layout. Overlaid, it covered the end of
             * the scrollable content.
             */
            if (uiState.showsStaleNotice) {
                StaleFeedNotice(onRefresh = onRefresh, onDismiss = onStaleNoticeDismiss)
            }
        }

        /*
         * This one stays overlaid, and that is the difference: it is
         * transient, responding to a gesture that just failed and vanishing
         * on dismissal. Shifting the feed to display it would move the
         * reading on every refused open. It never meets the staleness notice:
         * it only exists offline, where `showsStaleNotice` is false.
         */
        if (uiState.isOfflineOpenNoticeVisible) {
            OfflineOpenNotice(
                onDismiss = onOfflineNoticeDismiss,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The shared staleness notice, under this screen's test tags. */
@Composable
private fun StaleFeedNotice(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedStaleNotice(
        onRefresh = onRefresh,
        onDismiss = onDismiss,
        modifier = modifier.testTag(DiscoverTestTags.STALE_NOTICE),
        actionModifier = Modifier.testTag(DiscoverTestTags.STALE_NOTICE_REFRESH),
        dismissModifier = Modifier.testTag(DiscoverTestTags.STALE_NOTICE_DISMISS),
    )
}

@Composable
private fun FeedBody(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // No default: a fallback `rememberLazyListState` would create a second
    // scroll state, out of sync with the one the screen uses for
    // scroll-to-top after refresh.
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    val phase = uiState.phase

    when {
        uiState.articles.isNotEmpty() -> ArticleList(
            uiState = uiState,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onRefresh = onRefresh,
            onArticleClick = onArticleClick,
            onArticleShare = onArticleShare,
            modifier = modifier,
            listState = listState,
            onVisibilityChanged = onVisibilityChanged,
        )

        /*
         * A terminated session is treated as a wait, not an error: the
         * repository just invalidated the token and the root switch moves to
         * the login screen on its own (SPECS.md §3.4). Displaying a message
         * here would annotate a screen about to disappear.
         */
        phase == DiscoverPhase.InitialLoading ||
            phase == DiscoverPhase.SessionEnded -> FeedCentered(modifier) { LoadingIndicator() }

        phase is DiscoverPhase.Failed -> PullableMessage(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            FeedFailureOrRetry(
                uiState = uiState,
                failure = phase.failure,
                onRetry = onRetry,
                modifier = Modifier.testTag(DiscoverTestTags.FAILURE),
                retryModifier = Modifier.testTag(DiscoverTestTags.RETRY),
            )
        }

        else -> PullableMessage(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            EmptyFeedMessage()
        }
    }
}

/**
 * A screen without articles, still pullable (GOAL-025-T02).
 *
 * A reader who has read everything has no error to retry, only an empty
 * screen, and pull-to-refresh is what they try first. The gesture is added to
 * the existing commands rather than replacing them: the title-bar button
 * remains, and the failure screen keeps its retry action.
 *
 * A single-item `LazyColumn` where a `Box` would suffice for display:
 * pull-to-refresh is detected through nested scrolling, and a non-scrolling
 * container emits none, leaving the gesture inert, which is worse than its
 * absence. A list dispatches even when it has nothing to scroll.
 * `fillParentMaxSize` gives the content exactly the frame height, so the same
 * centering as before.
 *
 * Initial loading and session end do not go through here: one already has a
 * request in flight, the other is about to leave the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullableMessage(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(DiscoverTestTags.PULLABLE_MESSAGE),
        ) {
            item(key = MESSAGE_KEY) {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                    content = { content() },
                )
            }
        }
    }
}

/**
 * The feed and its pull-to-refresh gesture (SPECS.md §4.6).
 *
 * Pull-to-refresh is also armed on article-less states, via
 * [PullableMessage] (GOAL-025-T02).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleList(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // No default, like `FeedBody`: the state always comes from the screen.
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    PrefetchNextPage(
        positionState = listState,
        articleCount = uiState.articles.size,
        hasMoved = {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0 ||
                listState.isScrollInProgress
        },
        isNearEnd = {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && lastVisible >= uiState.articles.size - PREFETCH_DISTANCE
        },
        onLoadMore = onLoadMore,
    )

    if (onVisibilityChanged != null) {
        ObserveArticleVisibility(listState = listState, onVisibilityChanged = onVisibilityChanged)
    }

    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            /*
             * The default indicator paints its disc in `surfaceContainer`:
             * over an article card it blends in in light theme, hiding the
             * title it covers. The primary container detaches it from both
             * backgrounds, and its `onPrimaryContainer` arc stays readable in
             * both themes.
             */
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = uiState.isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(DiscoverTestTags.LIST),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Stable key: without it, inserting articles at the top
            // (SPECS.md §4.6) would recompose the whole list and shift the
            // ongoing reading. With it, the list repositions its first
            // visible item on its key rather than its rank, so articles
            // inserted above do not push the reading down.
            items(items = uiState.articles, key = ArticleUiModel::id) { article ->
                ArticleCard(
                    article = article,
                    onClick = { onArticleClick(article.id) },
                    onShare = { onArticleShare(article.id) },
                )
            }

            item(key = FOOTER_KEY) {
                FeedFooter(uiState = uiState, onRetry = onRetry)
            }
        }
    }
}

/** The shared offline banner (SPECS.md §5.2), under this screen's test tag. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    FeedOfflineBanner(
        message = stringResource(R.string.discover_offline_banner),
        modifier = modifier.testTag(DiscoverTestTags.OFFLINE_BANNER),
    )
}

/**
 * Open refused for lack of network (SPECS.md §5.2).
 *
 * The gesture failed, reading continues: the strip explains why nothing
 * happened and waits to be dismissed.
 */
@Composable
private fun OfflineOpenNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    FeedNotice(
        message = stringResource(R.string.discover_offline_open_blocked),
        actionLabel = stringResource(R.string.discover_offline_notice_dismiss),
        onAction = onDismiss,
        modifier = modifier.testTag(DiscoverTestTags.OFFLINE_NOTICE),
        actionModifier = Modifier.testTag(DiscoverTestTags.OFFLINE_NOTICE_DISMISS),
    )
}

/**
 * Periodically samples article visibility (SPECS.md §4.5).
 *
 * Periodically, not on each scroll: the threshold is a continuous duration,
 * and an article sitting still on screen produces no event, so a
 * scroll-driven measurement would never report it. The chosen cadence and
 * its justification are in [VISIBILITY_SAMPLING_PERIOD_MILLIS].
 *
 * `repeatOnLifecycle(RESUMED)` rather than a plain `LaunchedEffect`: a loop
 * tied only to composition would keep running with the screen off or the app
 * in the background, marking as read articles nobody is looking at, an
 * irreversible false positive since the marking is then sent to the server,
 * while waking the device five times per second. `RESUMED` rather than
 * `STARTED`: the only state where the screen is actually in the foreground
 * and not merely visible behind a dialog.
 *
 * The computation lives in an effect, never in a Composable body
 * (AGENTS.md §9): it reads a layout, it produces no display.
 */
@Composable
private fun ObserveArticleVisibility(
    listState: LazyListState,
    onVisibilityChanged: (Map<ArticleId, Float>) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(listState, onVisibilityChanged, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sampleVisibility(
                visibility = { listState.layoutInfo.articleVisibility() },
                onVisibilityChanged = onVisibilityChanged,
            )
        }
    }
}

/**
 * One card per article.
 *
 * The card is clickable only if the article has a link: SPECS.md §4.7
 * requires an article without a usable link not to be, and to make that
 * visible, hence the explicit mention rather than a click with no effect.
 */
@Composable
private fun ArticleCard(
    article: ArticleUiModel,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchTarget)
        .testTag(DiscoverTestTags.card(article.id))

    if (article.isOpenable) {
        Card(onClick = onClick, modifier = cardModifier) { ArticleCardContent(article, onShare) }
    } else {
        Card(modifier = cardModifier) { ArticleCardContent(article, onShare) }
    }
}

@Composable
private fun ArticleCardContent(article: ArticleUiModel, onShare: () -> Unit) {
    Column {
        if (article.hasIllustration) {
            ArticleIllustration(imageUrl = article.imageUrl, testTag = DiscoverTestTags.ILLUSTRATION)
        }

        /*
         * `fillMaxWidth` is not decorative: without it this column wraps its
         * content, and the share button's `align(End)` lines up on the text
         * width instead of the card width. The flaw only shows on an article
         * without an illustration and with short text; with one, the
         * illustration already forces full width (see GOAL-017-T02).
         */
        /*
         * No bottom padding when the footer has its button: the button
         * provides it. The 48 dp touch target surrounds a 16 dp glyph, so it
         * already leaves 16 points below the stroke, exactly the padding of
         * the other three sides. Adding more created a visible band. Going
         * lower would require shrinking the target below the 48 dp of
         * SPECS.md §7.1.
         *
         * The card without a link keeps its padding: without a button to
         * carry it, its last line would touch the edge.
         */
        val bottomPadding = if (article.isOpenable) Spacing.none else Spacing.md

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = bottomPadding),
            /*
             * No spacing between title and excerpt: the two styles' own line
             * height already separates them, and the extra 4 dp read as
             * excessive on device. The footer keeps that gap for itself.
             */
        ) {
            ArticleCardTexts(article)
            ArticleCardFooter(article = article, onShare = onShare)
        }
    }
}

/**
 * Card footer: source on the left, share on the right (GOAL-023).
 *
 * The title thereby becomes the first thing read, which benefits a screen
 * reader as much as the eye: the source is no longer announced before the
 * subject.
 *
 * `weight(1f)` on the text rather than a spacer: the text must yield when
 * the feed name is long, shortening with an ellipsis, rather than pushing
 * the command off the card.
 *
 * The button's click does not propagate to the card: an `IconButton`
 * consumes its own, so tapping share does not open the article.
 */
@Composable
private fun ArticleCardFooter(
    article: ArticleUiModel,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Deliberately no `heightIn` here. It had been added so the footer of a
     * link-less article, without a button, matched the height of the others.
     * The cost was visible on device: the button's 48 dp target, centered on
     * a 16 dp text line, left an empty band below it. The tighter card was
     * the requested choice.
     */
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.discover_article_meta,
                article.feedTitle,
                article.publishedAt.label(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (article.isOpenable) {
            ArticleShareButton(
                onShare = onShare,
                testTag = DiscoverTestTags.share(article.id),
            )
        }
    }
}

@Composable
private fun ColumnScope.ArticleCardTexts(article: ArticleUiModel) {
    // The source and date live in the card footer (`ArticleCardFooter`,
    // GOAL-023): the title opens the card.
    Text(
        text = article.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )

    if (article.excerpt.isNotBlank()) {
        Text(
            text = article.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (!article.isOpenable) {
        Text(
            text = stringResource(R.string.discover_article_no_link),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DiscoverTestTags.NO_LINK),
        )
    }
}

/**
 * What follows the last article.
 *
 * The end of the feed is stated, never merely endured: a list that stops
 * growing is indistinguishable from a failure (SPECS.md §4.4).
 */
@Composable
private fun FeedFooter(
    uiState: DiscoverUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = uiState.phase

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (phase) {
            DiscoverPhase.LoadingMore -> LoadingIndicator()

            DiscoverPhase.EndOfFeed -> FeedEndOfFeedMessage(Modifier.testTag(DiscoverTestTags.END_OF_FEED))

            is DiscoverPhase.Failed -> FeedFailureOrRetry(
                uiState = uiState,
                failure = phase.failure,
                onRetry = onRetry,
                modifier = Modifier.testTag(DiscoverTestTags.FAILURE),
                retryModifier = Modifier.testTag(DiscoverTestTags.RETRY),
            )

            // Nothing to say: the feed continues, or the session is ending.
            DiscoverPhase.Idle,
            DiscoverPhase.InitialLoading,
            DiscoverPhase.SessionEnded,
            -> Unit
        }
    }
}

@Composable
private fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    FeedEmptyMessage(modifier.testTag(DiscoverTestTags.EMPTY))
}

/** Footer key, distinct from any article identifier. */
private const val FOOTER_KEY = "discover:footer"

/** Key of the single item of an article-less screen. */
private const val MESSAGE_KEY = "discover:message"

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    AppTheme(dynamicColor = false) {
        DiscoverScreen(
            uiState = DiscoverUiState(
                articles = listOf(
                    ArticleUiModel(
                        id = 1L,
                        title = "Un titre d'article assez long pour tenir sur deux lignes",
                        feedTitle = "Le Monde",
                        publishedAt = RelativeTime.Hours(2),
                        excerpt = "Un extrait de l'article, écourté par l'application.",
                        imageUrl = "https://exemple.org/illustration.jpg",
                        isOpenable = true,
                    ),
                    ArticleUiModel(
                        id = 2L,
                        title = "Un article sans lien exploitable",
                        feedTitle = "Un flux mal formé",
                        publishedAt = RelativeTime.Days(3),
                        excerpt = "Sans illustration, et sans lien : la carte reste lisible.",
                        isOpenable = false,
                    ),
                ),
                phase = DiscoverPhase.EndOfFeed,
            ),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}

/** Offline mode: calm banner, cache intact, refused-open notice. */
@Preview(showBackground = true)
@Composable
private fun DiscoverScreenOfflinePreview() {
    AppTheme(dynamicColor = false) {
        DiscoverScreen(
            uiState = DiscoverUiState(
                articles = listOf(
                    ArticleUiModel(
                        id = 1L,
                        title = "Un article venu du cache, toujours lisible sans réseau",
                        feedTitle = "Le Monde",
                        publishedAt = RelativeTime.Hours(6),
                        excerpt = "Le contenu enregistré reste consultable : rien n'est vidé.",
                        isOpenable = true,
                    ),
                ),
                phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
                isOffline = true,
                isOfflineOpenNoticeVisible = true,
            ),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        DiscoverScreen(
            uiState = DiscoverUiState(phase = DiscoverPhase.EndOfFeed),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}

/**
 * Scrolls back to the top when a pull-to-refresh ends.
 *
 * SPECS.md §4.6: the gesture clears the list and restarts from the top.
 * Without this scroll, the user would stay at a rank that no longer refers to
 * anything they were looking at, the content having been replaced under
 * them. The falling edge lives in [AfterRefreshSettles], shared with the
 * immersive mode.
 */
@Composable
private fun ScrollToTopAfterRefresh(listState: LazyListState, isRefreshing: Boolean) {
    AfterRefreshSettles(isRefreshing) { listState.scrollToItem(0) }
}
