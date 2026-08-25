package fr.vbrosseau.freshrssdiscover.presentation.immersive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.label
import fr.vbrosseau.freshrssdiscover.presentation.discover.message
import fr.vbrosseau.freshrssdiscover.presentation.discover.sampleVisibility
import fr.vbrosseau.freshrssdiscover.presentation.feed.AfterRefreshSettles
import fr.vbrosseau.freshrssdiscover.presentation.feed.ArticleIllustration
import fr.vbrosseau.freshrssdiscover.presentation.feed.ArticleShareButton
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedCentered
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedEmptyMessage
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedFailureBlock
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedNotice
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedOfflineBanner
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRetryAction
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedStaleNotice
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing
import kotlinx.coroutines.flow.first

/**
 * Remaining-article threshold below which the next page is requested
 * (SPECS.md §4.4, GOAL-012-T02).
 *
 * Three, not the List mode's five: a swipe advances exactly one article, so
 * three full-screen articles mean at least three gestures plus reading time,
 * which comfortably covers the network round trip. A threshold of one would
 * make swipes hit the loading page at every page boundary.
 */
private const val PREFETCH_DISTANCE = 3

/** Key of the trailing page, distinct from any article id. */
private const val TRAILING_PAGE_KEY = "immersive:trailing"

/** Card corner radius, matching Material 3 large surfaces. */
private val CardShape = RoundedCornerShape(28.dp)

/**
 * Drop shadow of the top card.
 *
 * The shadow is what conveys a stack: without it, the smaller card underneath
 * reads as a drawn frame rather than a second object behind.
 */
private val CardElevation = 6.dp

/**
 * Rotation pivot of the card, below its bottom edge.
 *
 * 1.6 times the height from the top, well beyond the card. A central pivot
 * would spin the card like a needle; a pivot below produces the arc of an
 * object pushed aside by hand.
 */
private val CardPivot = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1.6f)

/**
 * Swipe-mode feed: one full-screen article (SPECS.md §4.8).
 *
 * Stateless with respect to business logic: it renders [uiState] and reports
 * gestures, which keeps it previewable and testable without the injection
 * graph. The only state it owns is the swipe position.
 *
 * @param onArticleShare no default value, as in List mode: an implicit `{}`
 *   would leave a visible but inert button with nothing to flag it.
 * @param onVisibilityChanged receiver of visibility samples (SPECS.md §4.5).
 *   Nullable and null by default: observation is a periodic loop, and running
 *   it without a receiver would waste battery and keep previews busy.
 */
@Composable
fun ImmersiveScreen(
    uiState: ImmersiveUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOfflineNoticeDismiss: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onStaleNoticeDismiss: () -> Unit = {},
    pagerState: PagerState = rememberPagerState { uiState.pageCount },
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    ReturnToFirstCardAfterRefresh(pagerState = pagerState, isRefreshing = uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Above the feed, not overlaid: the banner informs without hiding
            // content that remains readable (SPECS.md §5.2).
            if (uiState.showsOfflineBanner) OfflineBanner()

            ImmersiveBody(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                onArticleShare = onArticleShare,
                modifier = Modifier.weight(1f),
                pagerState = pagerState,
                onVisibilityChanged = onVisibilityChanged,
            )

            /*
             * Below the feed, not overlaid. The stale notice persists until
             * dismissed or the feed reloads: as an overlay it would cover the
             * end of the card's scrollable content, including the share
             * button, the only command in this mode since the whole card
             * opens the article (SPECS.md §4.7). A persistent notice takes
             * its place in the layout; only a transient notice may overlay.
             */
            if (uiState.showsStaleNotice) {
                StaleFeedNotice(onRefresh = onRefresh, onDismiss = onStaleNoticeDismiss)
            }
        }

        /*
         * This one stays overlaid: it is transient, reacting to a gesture
         * that just failed. It never coexists with the stale notice, which
         * does not exist offline.
         */
        if (uiState.isOfflineOpenNoticeVisible) {
            OfflineOpenNotice(
                onDismiss = onOfflineNoticeDismiss,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Notice shown when the displayed feed is several hours old (SPECS.md §4.6).
 *
 * Same strings and action as List mode: the command reuses the title bar's
 * refresh path rather than its own.
 */
@Composable
private fun StaleFeedNotice(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedStaleNotice(
        onRefresh = onRefresh,
        onDismiss = onDismiss,
        modifier = modifier.testTag(ImmersiveTestTags.STALE_NOTICE),
        actionModifier = Modifier.testTag(ImmersiveTestTags.STALE_NOTICE_REFRESH),
        dismissModifier = Modifier.testTag(ImmersiveTestTags.STALE_NOTICE_DISMISS),
    )
}

/**
 * Returns the pager to the first card when a refresh completes.
 *
 * Counterpart of `ScrollToTopAfterRefresh` in List mode, for the same reason:
 * SPECS.md §4.6 requires the refresh to be visible. Staying on the current
 * card after replacing the whole stack would leave the button with no
 * observable effect.
 *
 * The return happens on the transition from `true` to `false`, not during the
 * refresh: jumping on press would scroll a stack about to be discarded. The
 * falling-edge detection lives in [AfterRefreshSettles], shared with List.
 */
@Composable
private fun ReturnToFirstCardAfterRefresh(pagerState: PagerState, isRefreshing: Boolean) {
    AfterRefreshSettles(isRefreshing) { pagerState.scrollToPage(0) }
}

@Composable
private fun ImmersiveBody(
    uiState: ImmersiveUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // No default: a fallback `rememberPagerState` would create a second swipe
    // state, out of sync with the one the screen passes to the return-to-top.
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    val phase = uiState.phase

    when {
        uiState.articles.isNotEmpty() -> ArticlePager(
            uiState = uiState,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onArticleClick = onArticleClick,
            onArticleShare = onArticleShare,
            modifier = modifier,
            pagerState = pagerState,
            onVisibilityChanged = onVisibilityChanged,
        )

        // An ended session is a wait, not an error: the root router switches
        // to the login screen on its own (SPECS.md §3.4).
        phase == DiscoverPhase.InitialLoading ||
            phase == DiscoverPhase.SessionEnded -> FeedCentered(modifier) { LoadingIndicator() }

        phase is DiscoverPhase.Failed -> FeedCentered(modifier) {
            FailureBlock(failure = phase.failure, onRetry = onRetry)
        }

        else -> FeedCentered(modifier) { EmptyFeedMessage() }
    }
}

/**
 * The pager and its accessible alternative.
 *
 * The pager fills the remaining space; the navigation bar is always present,
 * never hidden (GOAL-012-T07). A horizontal swipe is usable neither with a
 * screen reader, which reserves that gesture for its own exploration, nor by
 * users lacking wrist precision or mobility. SPECS.md §7.1 requires the app
 * to remain usable; two 48 dp buttons make it fully operable without any
 * drag gesture.
 */
@Composable
private fun ArticlePager(
    uiState: ImmersiveUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // No default, as in `ImmersiveBody`: the state always comes from the screen.
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    val articleIds = remember(uiState.articles) { uiState.articles.map(ArticleUiModel::id) }

    PrefetchNextPage(pagerState = pagerState, articleCount = articleIds.size, onLoadMore = onLoadMore)

    if (onVisibilityChanged != null) {
        ObserveArticleVisibility(
            pagerState = pagerState,
            articleIds = articleIds,
            onVisibilityChanged = onVisibilityChanged,
        )
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .testTag(ImmersiveTestTags.PAGER),
        // Stable key: without it, inserting articles at the head would move
        // the displayed article under the finger.
        key = { page -> uiState.articles.getOrNull(page)?.id ?: TRAILING_PAGE_KEY },
    ) { page ->
        val article = uiState.articles.getOrNull(page)

        ImmersiveCard(pagerState = pagerState, page = page) {
            if (article == null) {
                TrailingPage(uiState = uiState, onRetry = onRetry)
            } else {
                ArticlePage(
                    article = article,
                    onOpen = { onArticleClick(article.id) },
                    onShare = { onArticleShare(article.id) },
                )
            }
        }
    }
}

/**
 * One card of the stack and its motion (GOAL-012-T09).
 *
 * The geometry is computed by [immersivePageTransform], outside any `Composable`
 * and tested separately; only its application remains here.
 *
 * Three details the pattern depends on:
 *
 * The inset. A card touching the edges cannot rotate: the rotation would
 * expose the background corners and read as a rendering glitch. The margin is
 * what gives the tilt room to exist; it is not decorative.
 *
 * The pivot below the card. A central rotation spins the card like a needle;
 * a pivot beyond the bottom edge produces the arc of an object pushed aside
 * by hand, which is the gesture being imitated.
 *
 * `graphicsLayer` rather than layout modifiers: nothing is remeasured on each
 * frame of the gesture. An animated `padding` would relayout a full screen of
 * text for every pixel travelled.
 */
@Composable
private fun ImmersiveCard(
    pagerState: PagerState,
    page: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transform = immersivePageTransform(
        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CardShape,
        shadowElevation = CardElevation,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .zIndex(transform.drawOrder)
            .graphicsLayer {
                translationX = transform.translationXFraction * size.width
                rotationZ = transform.rotationDegrees
                scaleX = transform.scale
                scaleY = transform.scale
                alpha = transform.alpha
                transformOrigin = CardPivot
            },
        content = content,
    )
}

/**
 * One article, full screen.
 *
 * Vertically scrollable by necessity: SPECS.md §7.1 requires the app to
 * remain usable at increased font size, where a 900-character excerpt
 * exceeds the screen. Without scrolling the end of the text would be
 * silently unreachable.
 *
 * The whole card opens the article (SPECS.md §4.7), as in List mode. Compose
 * distinguishes tap from drag, so the horizontal gesture is not consumed by
 * the click; `swipingLeftStillWorksWithAClickableCard` verifies it.
 *
 * `onClickLabel` rather than a visible label: the touch surface announces
 * nothing by itself, and a screen reader needs to know what the tap does.
 */
@Composable
private fun ArticlePage(
    article: ArticleUiModel,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.immersive_open_article)

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (article.isOpenable) {
                    Modifier.clickable(onClickLabel = openLabel, onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .verticalScroll(rememberScrollState())
            .testTag(ImmersiveTestTags.page(article.id)),
    ) {
        if (article.hasIllustration) {
            ArticleIllustration(imageUrl = article.imageUrl, testTag = ImmersiveTestTags.ILLUSTRATION)
        }

        ArticleText(article = article, onShare = onShare)
    }
}

/**
 * Article text and its share command.
 *
 * `fillMaxWidth` is not decorative: without it the column shrinks to its
 * text, and the share `align(End)` aligns to the content width instead of
 * the card width. List mode carries the same fix (see `GOAL-017-T02`).
 */
@Composable
private fun ArticleText(
    article: ArticleUiModel,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        /*
         * Share on the source line, matching List mode. Placed at the top of
         * the card rather than after an excerpt that can reach 1,400
         * characters, which would push the mode's only visible command below
         * the fold.
         *
         * `weight(1f)` on the text, as in List mode: the feed title truncates,
         * the command is never pushed off the card.
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.immersive_article_meta,
                    article.feedTitle,
                    article.publishedAt.label(),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (article.isOpenable) {
                ArticleShareButton(
                    onShare = onShare,
                    testTag = ImmersiveTestTags.share(article.id),
                )
            }
        }

        Text(text = article.title, style = MaterialTheme.typography.headlineSmall)

        if (article.excerpt.isNotBlank()) {
            Text(
                text = article.excerpt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        /*
         * A link-less article says so, as in List mode: the card is not
         * clickable and nothing else would signal it; a silent surface is
         * indistinguishable from one that stopped responding (SPECS.md §4.7).
         */
        if (!article.isOpenable) {
            Text(
                text = stringResource(R.string.immersive_article_no_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ImmersiveTestTags.NO_LINK),
            )
        }
    }
}

/**
 * Page shown after the last loaded article (GOAL-012-T03).
 *
 * The end of the feed is stated explicitly: a swipe that stops responding is
 * indistinguishable from a failure (SPECS.md §4.4).
 */
@Composable
private fun TrailingPage(
    uiState: ImmersiveUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val phase = uiState.phase) {
            DiscoverPhase.EndOfFeed -> EndOfFeedMessage()

            /*
             * Offline, the top banner already states the cause: repeating it
             * in red would turn two signals into an alarm while the displayed
             * content still works (SPECS.md §5.2). Only the retry remains.
             */
            is DiscoverPhase.Failed ->
                if (uiState.showsOfflineBanner) {
                    RetryAction(onRetry)
                } else {
                    FailureBlock(failure = phase.failure, onRetry = onRetry)
                }

            // Feed still loading or session ending: either way this page is
            // a wait, and shows it.
            else -> LoadingIndicator()
        }
    }
}

@Composable
private fun EndOfFeedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(Spacing.xl)
            .testTag(ImmersiveTestTags.END_OF_FEED),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.immersive_end_of_feed_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.immersive_end_of_feed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Requests the next page before the last article is reached (GOAL-012-T02).
 *
 * `derivedStateOf` avoids restarting the effect for every pixel of the
 * gesture: only crossing the threshold matters. The article count is part of
 * the key: a page shorter than the threshold would otherwise leave the
 * condition true without ever re-triggering the load, silently stalling the
 * feed.
 */
@Composable
private fun PrefetchNextPage(
    pagerState: PagerState,
    articleCount: Int,
    onLoadMore: () -> Unit,
) {
    // Nothing loads before an actual swipe, as in List mode: the cache with
    // read articles filtered out can hold fewer pages than the threshold, and
    // the load would then fire without any gesture, reintroducing the launch
    // request SPECS.md §5.1 removed (see List mode's `PrefetchNextPage`).
    // Latched by an effect, not written during composition, as in List.
    var hasSwiped by remember(pagerState) { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage > 0 || pagerState.isScrollInProgress }.first { it }
        hasSwiped = true
    }

    val shouldLoadMore by remember(pagerState, articleCount) {
        derivedStateOf { pagerState.currentPage >= articleCount - PREFETCH_DISTANCE }
    }

    LaunchedEffect(shouldLoadMore, articleCount, hasSwiped) {
        if (shouldLoadMore && hasSwiped) onLoadMore()
    }
}

/**
 * Periodically samples the visibility of the displayed article (GOAL-012-T01).
 *
 * Periodic, not gesture-driven: a still full-screen article produces no
 * events at all, so a movement-triggered measurement would never report it.
 * The cadence is List mode's [VISIBILITY_SAMPLING_PERIOD_MILLIS]: 5 Hz
 * locates the one-second threshold crossing within 20% without waking the
 * device needlessly.
 *
 * `repeatOnLifecycle(RESUMED)` rather than a plain `LaunchedEffect`: a loop
 * tied only to composition would keep running with the screen off and mark
 * the displayed article as read, an irreversible false positive since the
 * mark is then sent to the server.
 */
@Composable
private fun ObserveArticleVisibility(
    pagerState: PagerState,
    articleIds: List<Long>,
    onVisibilityChanged: (Map<ArticleId, Float>) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(pagerState, articleIds, onVisibilityChanged, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sampleVisibility(
                visibility = {
                    pagerVisibility(articleIds, pagerState.currentPage, pagerState.currentPageOffsetFraction)
                },
                onVisibilityChanged = onVisibilityChanged,
            )
        }
    }
}

/** Shared offline banner (SPECS.md §5.2). */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    FeedOfflineBanner(
        message = stringResource(R.string.immersive_offline_banner),
        modifier = modifier,
    )
}

/** Notice shown when opening is refused for lack of network (SPECS.md §5.2). */
@Composable
private fun OfflineOpenNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    FeedNotice(
        message = stringResource(R.string.immersive_offline_open_blocked),
        actionLabel = stringResource(R.string.immersive_offline_notice_dismiss),
        onAction = onDismiss,
        modifier = modifier.testTag(ImmersiveTestTags.OFFLINE_NOTICE),
        actionModifier = Modifier.testTag(ImmersiveTestTags.OFFLINE_NOTICE_DISMISS),
    )
}

@Composable
private fun FailureBlock(
    failure: DiscoverFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedFailureBlock(
        failure = failure,
        retryLabel = stringResource(R.string.immersive_retry),
        onRetry = onRetry,
        modifier = modifier.testTag(ImmersiveTestTags.FAILURE),
        retryModifier = Modifier.testTag(ImmersiveTestTags.RETRY),
    )
}

@Composable
private fun RetryAction(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FeedRetryAction(
        label = stringResource(R.string.immersive_retry),
        onRetry = onRetry,
        modifier = modifier.testTag(ImmersiveTestTags.RETRY),
    )
}

@Composable
private fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    FeedEmptyMessage(
        title = stringResource(R.string.immersive_empty_title),
        body = stringResource(R.string.immersive_empty_body),
        modifier = modifier.testTag(ImmersiveTestTags.EMPTY),
    )
}

@Preview(showBackground = true)
@Composable
private fun ImmersiveScreenPreview() {
    AppTheme(dynamicColor = false) {
        ImmersiveScreen(
            uiState = ImmersiveUiState(
                articles = listOf(
                    ArticleUiModel(
                        id = 1L,
                        title = "Le télescope spatial livre ses premières images de la nébuleuse",
                        feedTitle = "Le Monde — Sciences",
                        publishedAt = RelativeTime.Hours(2),
                        excerpt = "Après six mois de calibrage, l'instrument a transmis une série de " +
                            "clichés d'une précision inédite, que les astronomes analysent depuis lundi.",
                        isOpenable = true,
                    ),
                ),
                phase = DiscoverPhase.Idle,
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
private fun ImmersiveScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        ImmersiveScreen(
            uiState = ImmersiveUiState(phase = DiscoverPhase.EndOfFeed),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}
