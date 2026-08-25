package fr.vbrosseau.freshrssdiscover.presentation.immersive

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.label
import fr.vbrosseau.freshrssdiscover.presentation.discover.sampleVisibility
import fr.vbrosseau.freshrssdiscover.presentation.feed.AfterRefreshSettles
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
 * Three, not the List mode's five: a flick advances exactly one article, so
 * three full-screen articles mean at least three gestures plus reading time,
 * which comfortably covers the network round trip. A threshold of one would
 * make flicks hit the loading page at every page boundary.
 */
private const val PREFETCH_DISTANCE = 3

/** Key of the trailing page, distinct from any article id. */
private const val TRAILING_PAGE_KEY = "immersive:trailing"

/**
 * Pages composed beyond the visible one, on each side.
 *
 * One: the next illustration starts loading while the current article is
 * being read, so a flick lands on a picture rather than on a tinted slot.
 * More would fetch images for articles that may never be reached.
 */
private const val PAGES_KEPT_AROUND = 1

/**
 * Lines of excerpt shown before the ellipsis.
 *
 * The page does not scroll: on a vertical pager, that gesture is the pager's.
 * Eight lines of `bodyLarge` are what a phone holds under a title and above
 * the bottom margin; the tap opens the article, which is where the full text
 * lives (SPECS.md §4.7).
 */
private const val EXCERPT_MAX_LINES = 8

/** Lines of title before the ellipsis: past three, the excerpt has no room left. */
private const val TITLE_MAX_LINES = 3

/**
 * Where the scrim begins fading in, as a fraction of the page height.
 *
 * The upper part of the illustration is left untouched: the scrim is there
 * for the text, not to dim the picture.
 */
private const val SCRIM_START = 0.35f

/**
 * Opacity of the scrim at the bottom edge.
 *
 * Not fully opaque: the picture must still be sensed under the text. At 0.92
 * the theme background dominates enough for `onSurface` to keep AA contrast
 * over any photograph, which a lower value cannot guarantee.
 */
private const val SCRIM_END_ALPHA = 0.92f

/**
 * Below this background luminance the theme is dark and the text light: the
 * source tint must then be deep rather than pastel. Halfway between the two
 * theme backgrounds, which sit far apart on the luminance scale.
 */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * Opacity of the source's initial over its tint.
 *
 * A watermark, not a letter to read: at 0.08 it gives the page a shape
 * without competing with the title; at 0.15 the eye kept reading it first.
 */
private const val MONOGRAM_ALPHA = 0.08f

/** Size of the watermark: taller than the page's upper half, so it is cropped rather than framed. */
private val MonogramSize = 520.sp

/** How far the watermark runs past the top-right corner: a letter fully inside the page reads as a logo. */
private val MonogramOverhang = 72.dp

/**
 * Blur radius and overscan of the backdrop copy: the List card's values
 * (`ArticleIllustration`), for the same reasons — wide enough that the
 * subject is no longer readable, enlarged enough that the blur's edge fade
 * never shows.
 */
private val BLUR_RADIUS = 32.dp
private const val BLUR_OVERSCAN = 1.1f

/** Corners of a picture set down on the page: Material's medium radius, softer than a screenshot's edge. */
private val PictureShape = RoundedCornerShape(12.dp)

/**
 * Dimming of the blurred copy behind a framed picture.
 *
 * Toward the theme background, not black: in the light theme a bright
 * photograph's halo turned the top of the page white, and a dark veil there
 * would have fought the theme instead of the halo.
 */
private const val BLUR_DIM_ALPHA = 0.35f

/** Room left on each side of a tilted picture so its corners stay inside the page. */
private val TiltInset = 20.dp

/** Shadow under a tilted picture: an object on the backdrop, not a frame drawn on it. */
private val TiltShadow = 12.dp

/** `Modifier.blur` only takes effect from Android 12 (API 31), as on the List card. */
private val supportsBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Where the top scrim has fully faded out, as a fraction of the page height.
 *
 * The title bar sits transparent over the page (SPECS.md §4.8): the picture
 * runs under it, and this gradient is what keeps the title and its actions
 * legible on any photograph. It ends well above the middle so the picture
 * is not dimmed twice.
 */
private const val TOP_SCRIM_END = 0.22f

/**
 * Opacity of the top scrim at the very edge.
 *
 * Lighter than the bottom one: a bar title is short and bold, while the
 * excerpt below is body text; the picture deserves to show through more
 * here.
 */
private const val TOP_SCRIM_START_ALPHA = 0.75f

/**
 * Immersive feed: one article per screen, flicked vertically (SPECS.md §4.8).
 *
 * Stateless with respect to business logic: it renders [uiState] and reports
 * gestures, which keeps it previewable and testable without the injection
 * graph. The only state it owns is the page position.
 *
 * @param onArticleShare no default value, as in List mode: an implicit `{}`
 *   would leave a visible but inert button with nothing to flag it.
 * @param onVisibilityChanged receiver of visibility samples (SPECS.md §4.5).
 *   Nullable and null by default: observation is a periodic loop, and running
 *   it without a receiver would waste battery and keep previews busy.
 * @param topInset height of the transparent bar the pages run under. The
 *   pages ignore it — that is the point — but the offline banner must not:
 *   text under the title would be unreadable.
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
    topInset: Dp = Spacing.none,
) {
    ReturnToFirstPageAfterRefresh(pagerState = pagerState, isRefreshing = uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Above the feed, not overlaid: the banner informs without hiding
            // content that remains readable (SPECS.md §5.2).
            if (uiState.showsOfflineBanner) OfflineBanner(modifier = Modifier.padding(top = topInset))

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
             * bottom of the page, where the text and the share button live —
             * the only command in this mode since the whole page opens the
             * article (SPECS.md §4.7). A persistent notice takes its place in
             * the layout; only a transient notice may overlay.
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
 * Returns the pager to the first page when a refresh completes.
 *
 * Counterpart of `ScrollToTopAfterRefresh` in List mode, for the same reason:
 * SPECS.md §4.6 requires the refresh to be visible. Staying on the current
 * page after replacing the whole feed would leave the button with no
 * observable effect.
 *
 * The return happens on the transition from `true` to `false`, not during the
 * refresh: jumping on press would scroll a feed about to be discarded. The
 * falling-edge detection lives in [AfterRefreshSettles], shared with List.
 */
@Composable
private fun ReturnToFirstPageAfterRefresh(pagerState: PagerState, isRefreshing: Boolean) {
    AfterRefreshSettles(isRefreshing) { pagerState.scrollToPage(0) }
}

@Composable
private fun ImmersiveBody(
    uiState: ImmersiveUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // No default: a fallback `rememberPagerState` would create a second
    // page state, out of sync with the one the screen passes to the
    // return-to-top.
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
 * The vertical pager: one article per page, snapping to the nearest one.
 *
 * `VerticalPager` rather than a lazy list with snapping: the pager owns the
 * one-page-per-flick rule, the settled-page notion the visibility sampling
 * relies on, and the offset the page transition is computed from.
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

    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(ImmersiveTestTags.PAGER),
        beyondViewportPageCount = PAGES_KEPT_AROUND,
        // Stable key: without it, inserting articles at the head would move
        // the displayed article under the finger.
        key = { page -> uiState.articles.getOrNull(page)?.id ?: TRAILING_PAGE_KEY },
    ) { page ->
        val article = uiState.articles.getOrNull(page)
        // A lambda, not a value: read inside `graphicsLayer` blocks, the
        // offset is sampled at draw time on every frame of the gesture,
        // without recomposing the page. Read here, it would be one frame
        // behind the finger — the lag observed on device (2026-08-25).
        val pageOffset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction }

        ImmersivePage(pageOffset = pageOffset) {
            if (article == null) {
                TrailingPage(uiState = uiState, onRetry = onRetry)
            } else {
                ArticlePage(
                    article = article,
                    pageOffset = pageOffset,
                    onOpen = { onArticleClick(article.id) },
                    onShare = { onArticleShare(article.id) },
                )
            }
        }
    }
}

/**
 * One page and its motion.
 *
 * The geometry is computed by [immersivePageTransform], outside any
 * `Composable` and tested separately; only its application remains here.
 * `graphicsLayer` rather than layout modifiers: nothing is remeasured on each
 * frame of the gesture, and [pageOffset] is read in the draw phase, in step
 * with the finger.
 */
@Composable
private fun ImmersivePage(
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val transform = immersivePageTransform(pageOffset())
                scaleX = transform.scale
                scaleY = transform.scale
                alpha = transform.alpha
            },
        content = { content() },
    )
}

/**
 * One article, filling the screen.
 *
 * Three layers, bottom to top: the illustration as a backdrop, a scrim that
 * fades into the theme background over the lower part, then the text and
 * the action rail. The scrim is what lets `onSurface` sit on any photograph
 * in either theme without a second colour scheme.
 *
 * The whole page opens the article (SPECS.md §4.7), as in List mode. Compose
 * distinguishes tap from drag, so the vertical gesture is not consumed by
 * the click; `flickingUpStillWorksWithAClickablePage` verifies it.
 *
 * `onClickLabel` rather than a visible label: the touch surface announces
 * nothing by itself, and a screen reader needs to know what the tap does.
 */
@Composable
private fun ArticlePage(
    article: ArticleUiModel,
    pageOffset: () -> Float,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.immersive_open_article)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (article.isOpenable) {
                    Modifier.clickable(onClickLabel = openLabel, onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .testTag(ImmersiveTestTags.page(article.id)),
    ) {
        if (article.hasIllustration) {
            Backdrop(
                articleId = article.id,
                feedTitle = article.feedTitle,
                imageUrl = article.imageUrl,
                pageOffset = pageOffset,
            )
        } else {
            SourceBackdrop(feedTitle = article.feedTitle, modifier = Modifier.crossfading(pageOffset))
        }

        Scrim()
        TopScrim()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(Spacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            ArticleText(article = article, modifier = Modifier.weight(1f))

            if (article.isOpenable) {
                ActionRail(onShare = onShare, shareTestTag = ImmersiveTestTags.share(article.id))
            }
        }
    }
}

/**
 * What the page stands on when the article has no picture (SPECS.md §4.8).
 *
 * A tint that belongs to the source, computed by [sourcePalette], and the
 * source's initial as a watermark: full screen, an article without
 * illustration would otherwise leave two thirds of the page empty, the hole
 * SPECS.md §4.3 forbids. A single theme colour was tried first; every such
 * page looked like the same page.
 *
 * The theme is read from the background's luminance rather than passed
 * down: the page has no reason to know how the theme was chosen, only
 * whether the text on it is light or dark.
 *
 * The watermark is decorative and hidden from accessibility: a screen
 * reader would otherwise announce a lone letter before the source line.
 */
@Composable
private fun SourceBackdrop(feedTitle: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = remember(feedTitle, scheme.surface) {
        sourcePalette(feedTitle = feedTitle, dark = scheme.surface.luminance() < DARK_SURFACE_LUMINANCE)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
    ) {
        Text(
            text = palette.monogram,
            style = MaterialTheme.typography.displayLarge,
            fontSize = MonogramSize,
            fontWeight = FontWeight.Black,
            color = scheme.onSurface.copy(alpha = MONOGRAM_ALPHA),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = MonogramOverhang, y = -MonogramOverhang)
                .clearAndSetSemantics { },
        )
    }
}

/**
 * The illustration as the page's background (SPECS.md §4.3).
 *
 * Cropped to fill: full screen, a letterboxed picture would leave two bands
 * that no scrim can dress. A load failure leaves no trace, as in List mode:
 * the page then stands on the tinted backdrop alone, indistinguishable from
 * an article that has none.
 *
 * Decorative, no description (SPECS.md §7.1): the feed provides no alt text.
 *
 * The parallax is applied here and not on the page: the text must follow the
 * finger exactly, only the scene behind it lags.
 */
@Composable
private fun Backdrop(
    articleId: Long,
    feedTitle: String,
    imageUrl: String?,
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()
    val source = (state as? AsyncImagePainter.State.Success)?.result?.image

    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val sourceRatio = if (source != null && source.height > 0) source.width.toFloat() / source.height else 1f
    val fit = backdropFit(
        articleId = articleId,
        sourceWidthPx = source?.width ?: 0,
        sourceHeightPx = source?.height ?: 0,
        pageWidthPx = pageSize.width,
        pageHeightPx = pageSize.height,
    )
    val dressed = supportsBlur && fit != BackdropFit.Full
    val tilted = dressed && fit == BackdropFit.Tilted

    /*
     * The tint stays outside the moving layer: it is the page, and the page
     * does not lean or lag — only the photograph set down on it does
     * (author's ruling, 2026-08-25). It also stands in while the picture
     * is not there — still loading, or never coming: a page announcing an
     * illustration it cannot show used to be plain black (seen on device).
     */
    if (tilted || source == null) SourceBackdrop(feedTitle = feedTitle, modifier = Modifier.crossfading(pageOffset))

    if (imageUrl == null || state is AsyncImagePainter.State.Error) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { pageSize = it }
            .graphicsLayer {
                val transform = immersivePageTransform(pageOffset())
                translationY = transform.backdropTranslationYFraction * size.height
                alpha = transform.backdropAlpha
            }
            .testTag(ImmersiveTestTags.ILLUSTRATION),
    ) {
        /*
         * Same recipe as the List card (SPECS.md §4.3, GOAL-016) whenever
         * the picture is shown whole: a blurred, cropped copy fills the
         * page and carries its colours; the sharp original sits on it.
         * The tilted look stands on the source's tint instead: a
         * photograph set down on a coloured page, not on a smear of
         * itself. Which look an article gets is [backdropFit]'s draw.
         */
        if (dressed && !tilted) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(BLUR_OVERSCAN)
                    .blur(BLUR_RADIUS),
            )
            // A blurred copy of a bright picture came out as a white halo
            // (seen on device, 2026-08-25): dimmed, it recedes behind the
            // sharp one instead of competing with it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = BLUR_DIM_ALPHA)),
            )
        }

        SharpPicture(painter = painter, fit = fit, dressed = dressed, articleId = articleId, sourceRatio = sourceRatio)
    }
}

/**
 * The picture itself, laid out according to its draw.
 *
 * Centred, by the author's ruling (2026-08-25): the picture is the page's
 * subject, and the scrim below keeps the text readable should the two meet.
 */
@Composable
private fun SharpPicture(
    painter: AsyncImagePainter,
    fit: BackdropFit,
    dressed: Boolean,
    articleId: Long,
    sourceRatio: Float,
) {
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = when {
            !dressed -> ContentScale.Crop
            fit == BackdropFit.Native -> ContentScale.Inside
            else -> ContentScale.FillWidth
        },
        alignment = Alignment.Center,
        modifier = when {
            dressed && fit == BackdropFit.Tilted ->
                Modifier.setDown(tilt = tiltDegrees(articleId), sourceRatio = sourceRatio)
            dressed && fit == BackdropFit.Framed -> Modifier.setDown(tilt = 0f, sourceRatio = sourceRatio)
            else -> Modifier.fillMaxSize()
        },
    )
}

/**
 * Fades a backdrop as its page leaves, so two backdrops crossfade through
 * the page background while they slide (author's request, 2026-08-25).
 * Read in the draw phase, like the parallax.
 */
private fun Modifier.crossfading(pageOffset: () -> Float): Modifier = graphicsLayer {
    alpha = immersivePageTransform(pageOffset()).backdropAlpha
}

/**
 * A photograph set down on the page: inset, rounded, shadowed, and tilted
 * or not. The framed and tilted looks are the same object, one of them
 * straight (author's ruling, 2026-08-25): a full-width picture ending on a
 * raw edge read as a band, not as a photograph.
 *
 * Sized to the picture, not to the page: a `graphicsLayer` casts its shadow
 * on its own bounds, and a page-sized layer drew a ghost rectangle of
 * shadow around a picture half its height — seen on device (2026-08-25).
 * The inset is what gives the tilt room — a full-width picture rotated
 * would push its corners out of the page.
 */
private fun Modifier.setDown(tilt: Float, sourceRatio: Float): Modifier = this
    .fillMaxHeight()
    .wrapContentHeight(Alignment.CenterVertically)
    .padding(horizontal = TiltInset)
    .fillMaxWidth()
    .aspectRatio(sourceRatio)
    .graphicsLayer {
        rotationZ = tilt
        shadowElevation = TiltShadow.toPx()
        shape = PictureShape
        clip = true
    }

/**
 * Gradient from nothing to the theme background, over the lower part of the
 * page.
 *
 * Drawn whether or not there is an illustration: without one the gradient is
 * invisible on its own background, and one code path serves both cases.
 */
@Composable
private fun Scrim(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    SCRIM_START to Color.Transparent,
                    1f to surface.copy(alpha = SCRIM_END_ALPHA),
                ),
            ),
    )
}

/** Gradient from the theme background to nothing, under the transparent title bar. */
@Composable
private fun TopScrim(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to surface.copy(alpha = TOP_SCRIM_START_ALPHA),
                    TOP_SCRIM_END to Color.Transparent,
                ),
            ),
    )
}

/**
 * The commands of the page, stacked at the right edge.
 *
 * A rail rather than a row under the text: the text block keeps the whole
 * width for its lines, and the thumb reaches the rail without crossing them.
 * Sharing is the only command today; the rail is where the next one lands.
 */
@Composable
private fun ActionRail(
    onShare: () -> Unit,
    shareTestTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArticleShareButton(onShare = onShare, testTag = shareTestTag)
    }
}

/**
 * Source line, title and excerpt, in that order.
 *
 * The source first, as everywhere in the feed: without it, interleaving the
 * feeds would be disorienting (SPECS.md §4.3).
 */
@Composable
private fun ArticleText(article: ArticleUiModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        )

        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )

        if (article.excerpt.isNotBlank()) {
            Text(
                text = article.excerpt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = EXCERPT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }

        /*
         * A link-less article says so, as in List mode: the page is not
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
 * The end of the feed is stated explicitly: a flick that stops responding is
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
    // Nothing loads before an actual gesture, as in List mode: the cache with
    // read articles filtered out can hold fewer pages than the threshold, and
    // the load would then fire without any gesture, reintroducing the launch
    // request SPECS.md §5.1 removed (see List mode's `PrefetchNextPage`).
    // Latched by an effect, not written during composition, as in List.
    var hasFlicked by remember(pagerState) { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage > 0 || pagerState.isScrollInProgress }.first { it }
        hasFlicked = true
    }

    val shouldLoadMore by remember(pagerState, articleCount) {
        derivedStateOf { pagerState.currentPage >= articleCount - PREFETCH_DISTANCE }
    }

    LaunchedEffect(shouldLoadMore, articleCount, hasFlicked) {
        if (shouldLoadMore && hasFlicked) onLoadMore()
    }
}

/**
 * Periodically samples the visibility of the displayed article (GOAL-012-T01).
 *
 * Periodic, not gesture-driven: a still full-screen article produces no
 * events at all, so a movement-triggered measurement would never report it.
 * The cadence is List mode's [VISIBILITY_SAMPLING_PERIOD_MILLIS]: 5 Hz
 * locates the threshold crossing within one period without waking the
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
