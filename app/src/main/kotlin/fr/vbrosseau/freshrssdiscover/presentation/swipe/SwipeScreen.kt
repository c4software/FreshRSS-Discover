package fr.vbrosseau.freshrssdiscover.presentation.swipe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPosition
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.label
import fr.vbrosseau.freshrssdiscover.presentation.discover.message
import fr.vbrosseau.freshrssdiscover.presentation.discover.sampleVisibility
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedNotice
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * Nombre d'articles restants sous lequel la page suivante est demandée
 * (SPECS.md §4.4, GOAL-012-T02).
 *
 * Trois, et non les cinq du mode Liste, parce que le geste n'a pas le même
 * débit : un balayage avance d'exactement **un** article, là où un défilement
 * en traverse plusieurs d'un coup. Trois articles plein écran représentent donc
 * au moins trois gestes et le temps de lecture qui les sépare — bien plus que
 * l'aller-retour réseau qu'il s'agit de couvrir. Descendre à un seul article
 * ferait au contraire buter le balayage sur la page de chargement à chaque fin
 * de page.
 */
private const val PREFETCH_DISTANCE = 3

/**
 * Rapport largeur/hauteur du créneau d'illustration.
 *
 * Dimensionné par ce rapport, **jamais** par la taille de l'image reçue : une
 * hauteur déduite de l'image changerait au moment où elle arrive, et le contenu
 * sauterait sous les yeux du lecteur.
 */
private const val ILLUSTRATION_ASPECT_RATIO = 16f / 9f

/**
 * Opacité de la teinte qui marque le créneau pendant le chargement.
 *
 * Appliquée à `onSurface`, c'est-à-dire à la couleur *opposée* au fond : elle
 * assombrit en thème clair et éclaircit en thème sombre. `surfaceVariant`, lui,
 * se confond avec le fond en thème clair — un réservé d'image de contraste 1,00
 * a déjà été livré ainsi dans ce dépôt.
 */
private const val ILLUSTRATION_PLACEHOLDER_ALPHA = 0.12f

/** Clé de la page de fin, distincte de tout identifiant d'article. */
private const val TRAILING_PAGE_KEY = "swipe:trailing"

/** Cible tactile minimale (SPECS.md §7.1) : Material s'arrête à 40 dp. */
private val MinTouchTarget = 48.dp

/** Arrondi de la carte, celui des surfaces larges de Material 3. */
private val CardShape = RoundedCornerShape(28.dp)

/**
 * Ombre portée de la carte du dessus.
 *
 * C'est elle qui dit qu'il y a une **pile** : sans ombre, la carte du dessous,
 * simplement plus petite, se lirait comme un cadre dessiné autour de l'autre
 * plutôt que comme un second objet placé derrière.
 */
private val CardElevation = 6.dp

/**
 * Le point autour duquel la carte pivote : sous son bord inférieur.
 *
 * `1,6` fois la hauteur depuis le haut, donc bien au-delà de la carte. Un pivot
 * central la ferait tourner sur elle-même comme une aiguille ; placé en
 * dessous, il produit l'arc d'un objet que l'on écarte de la main.
 */
private val CardPivot = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1.6f)

/**
 * Le flux en mode Balayage : un article en plein écran (SPECS.md §4.8).
 *
 * Sans état métier : il affiche [uiState] et remonte les gestes, ce qui le rend
 * prévisualisable et testable sans graphe d'injection. Le seul état qu'il tient
 * est la position du balayage, qui n'appartient qu'à lui.
 *
 * @param onVisibilityChanged destinataire des relevés de visibilité (SPECS.md
 *   §4.5). **Nullable, et nul par défaut** : l'observation est une boucle
 *   périodique, et l'armer sans destinataire ferait tourner un minuteur pour
 *   jeter son résultat — batterie dépensée pour rien, et prévisualisations
 *   perpétuellement occupées.
 */
@Composable
fun SwipeScreen(
    uiState: SwipeUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOfflineNoticeDismiss: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onStaleNoticeDismiss: () -> Unit = {},
    onCurrentArticleChanged: (ArticleId?, Long) -> Unit = { _, _ -> },
    onPositionRestored: () -> Unit = {},
    positionToRestore: ReadingPosition? = null,
    pagerState: PagerState = rememberPagerState { uiState.pageCount },
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    RestoreReadingPosition(
        pagerState = pagerState,
        articles = uiState.articles,
        positionToRestore = positionToRestore,
        onPositionRestored = onPositionRestored,
    )
    RememberReadingPosition(
        pagerState = pagerState,
        articles = uiState.articles,
        onCurrentArticleChanged = onCurrentArticleChanged,
    )
    ReturnToFirstCardAfterRefresh(pagerState = pagerState, isRefreshing = uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Au-dessus du flux et non par-dessus : le bandeau informe, il ne
            // masque rien de ce qui reste lisible (SPECS.md §5.2).
            if (uiState.showsOfflineBanner) OfflineBanner()

            SwipeBody(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                modifier = Modifier.weight(1f),
                pagerState = pagerState,
                onVisibilityChanged = onVisibilityChanged,
            )

            /*
             * **Sous le flux et non par-dessus.** L'avis d'ancienneté dure
             * jusqu'à ce qu'on l'acquitte ou qu'on recharge : posé en
             * surimpression, il recouvrait la fin du contenu défilable de la
             * carte, c'est-à-dire le bouton d'ouverture de l'article — la
             * seule commande de ce mode (SPECS.md §4.7), et elle devenait
             * inatteignable sur un article long. Un avis qui s'installe prend
             * sa place dans la mise en page ; seul un avis fugace se superpose.
             */
            if (uiState.showsStaleNotice) {
                StaleFeedNotice(onRefresh = onRefresh, onDismiss = onStaleNoticeDismiss)
            }
        }

        /*
         * Celui-ci reste **en surimpression** : il est fugace, il répond à un
         * geste qui vient d'échouer. Il ne rencontre jamais l'avis
         * d'ancienneté, qui n'existe pas hors ligne.
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
 * Le flux affiché date de plusieurs heures (SPECS.md §4.6).
 *
 * Mêmes chaînes qu'en mode Liste, même action : c'est le rechargement de la
 * barre de titre que la commande emprunte, et non un chemin à elle.
 */
@Composable
private fun StaleFeedNotice(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedNotice(
        message = stringResource(R.string.feed_stale_notice),
        actionLabel = stringResource(R.string.feed_stale_refresh),
        onAction = onRefresh,
        modifier = modifier.testTag(SwipeTestTags.STALE_NOTICE),
        actionModifier = Modifier.testTag(SwipeTestTags.STALE_NOTICE_REFRESH),
        dismissLabel = stringResource(R.string.feed_stale_dismiss),
        onDismiss = onDismiss,
        dismissModifier = Modifier.testTag(SwipeTestTags.STALE_NOTICE_DISMISS),
    )
}

/**
 * Ramène le balayage à la première carte à la fin d'un rechargement.
 *
 * L'exact pendant de `ScrollToTopAfterRefresh` en mode Liste, et pour la même
 * raison : SPECS.md §4.6 veut que le rechargement se **voie**. Rester sur la
 * carte courante après avoir remplacé toute la pile donnerait un bouton dont
 * rien n'atteste l'effet.
 *
 * Le retour a lieu au **passage** de `true` à `false`, pas pendant : y aller
 * dès l'appui ferait défiler une pile que l'on s'apprête à jeter.
 */
/**
 * Reprend la lecture là où elle s'était arrêtée (SPECS.md §5.3).
 *
 * **Au plus proche, et c'est ce qui rend la reprise possible ici.** En plein
 * écran, l'article quitté est presque toujours celui que le marquage vient de
 * rendre lu : le flux des non-lus ne le contient plus au lancement suivant.
 * Chercher son identifiant exact échouerait donc à tous les coups, et le
 * balayage repartirait invariablement du premier article. `indexIn` retient à
 * la place le premier article qui n'est pas **plus récent** — celui qui suivait
 * immédiatement.
 *
 * C'est la même règle qu'en mode Liste, et volontairement le même code de
 * domaine : deux reprises calculées séparément finiraient par ne plus désigner
 * le même article, et basculer de mode déplacerait la lecture.
 */
@Composable
private fun RestoreReadingPosition(
    pagerState: PagerState,
    articles: List<ArticleUiModel>,
    positionToRestore: ReadingPosition?,
    onPositionRestored: () -> Unit,
) {
    LaunchedEffect(positionToRestore, articles) {
        if (positionToRestore == null || articles.isEmpty()) return@LaunchedEffect

        val index = positionToRestore.indexIn(
            articles.map { ReadingPosition.Candidate(it.id, it.publishedAtEpochSeconds) },
        )
        if (index != null) {
            pagerState.scrollToPage(index)
            onPositionRestored()
        }
    }
}

/**
 * Retient l'article sous les yeux.
 *
 * `settledPage` et non `currentPage` : le second bascule dès que le geste
 * dépasse la moitié de l'écran, y compris quand le doigt revient en arrière. On
 * enregistrerait alors une position que l'utilisateur n'a jamais atteinte.
 */
@Composable
private fun RememberReadingPosition(
    pagerState: PagerState,
    articles: List<ArticleUiModel>,
    onCurrentArticleChanged: (ArticleId?, Long) -> Unit,
) {
    val current by remember(pagerState, articles) {
        derivedStateOf { articles.getOrNull(pagerState.settledPage) }
    }

    LaunchedEffect(current) {
        current?.let { onCurrentArticleChanged(ArticleId(it.id), it.publishedAtEpochSeconds) }
    }
}

@Composable
private fun ReturnToFirstCardAfterRefresh(pagerState: PagerState, isRefreshing: Boolean) {
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) pagerState.scrollToPage(0)
        wasRefreshing = isRefreshing
    }
}

@Composable
private fun SwipeBody(
    uiState: SwipeUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { uiState.pageCount },
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    val phase = uiState.phase

    when {
        uiState.articles.isNotEmpty() -> ArticlePager(
            uiState = uiState,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onArticleClick = onArticleClick,
            modifier = modifier,
            pagerState = pagerState,
            onVisibilityChanged = onVisibilityChanged,
        )

        // Une session terminée est une attente, pas une erreur : l'aiguillage
        // racine bascule de lui-même vers la connexion (SPECS.md §3.4).
        phase == DiscoverPhase.InitialLoading ||
            phase == DiscoverPhase.SessionEnded -> Centered(modifier) { LoadingIndicator() }

        phase is DiscoverPhase.Failed -> Centered(modifier) {
            FailureBlock(failure = phase.failure, onRetry = onRetry)
        }

        else -> Centered(modifier) { EmptyFeedMessage() }
    }
}

/**
 * Le balayage lui-même, et son alternative.
 *
 * Le pagineur occupe tout ce qui reste ; la barre de navigation est **toujours
 * présente**, jamais escamotée. C'est la réponse à GOAL-012-T07 : un balayage
 * horizontal n'est praticable ni par un lecteur d'écran, qui réserve ce geste à
 * sa propre exploration, ni par qui manque de précision ou de mobilité dans le
 * poignet. SPECS.md §7.1 exige que l'application reste utilisable ; deux
 * boutons de 48 dp la rendent entièrement pilotable sans jamais faire glisser
 * le doigt.
 */
@Composable
private fun ArticlePager(
    uiState: SwipeUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { uiState.pageCount },
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
            .testTag(SwipeTestTags.PAGER),
        // Clé stable : sans elle, l'insertion d'articles en tête déplacerait
        // l'article affiché sous le doigt.
        key = { page -> uiState.articles.getOrNull(page)?.id ?: TRAILING_PAGE_KEY },
    ) { page ->
        val article = uiState.articles.getOrNull(page)

        SwipeCard(pagerState = pagerState, page = page) {
            if (article == null) {
                TrailingPage(uiState = uiState, onRetry = onRetry)
            } else {
                ArticlePage(article = article, onOpen = { onArticleClick(article.id) })
            }
        }
    }
}

/**
 * Une carte de la pile, et son mouvement (GOAL-012-T09).
 *
 * La géométrie est calculée par [swipeCardTransform], hors de tout `Composable`
 * et éprouvée à part ; il ne reste ici que de l'application.
 *
 * **Trois détails sans lesquels le motif ne fonctionne pas :**
 *
 * L'**encart**. Une carte qui touche les bords ne peut pas pivoter : la
 * rotation découvrirait les coins du fond, et le mouvement se lirait comme un
 * défaut d'affichage. La marge est ce qui donne à l'inclinaison un endroit où
 * exister — elle n'est pas décorative.
 *
 * Le **pivot sous la carte**. Une rotation autour du centre fait tourner la
 * carte sur elle-même, comme une aiguille. Placé au-delà du bord inférieur, le
 * pivot produit l'arc d'un objet que l'on écarte de la main, ce qui est le
 * geste que l'on imite.
 *
 * `graphicsLayer` **et non des modificateurs de mise en page** : rien n'est
 * remesuré à chaque image du geste. Un `padding` animé relancerait la mise en
 * page du texte à chaque pixel parcouru, sur un écran entier de contenu.
 */
@Composable
private fun SwipeCard(
    pagerState: PagerState,
    page: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transform = swipeCardTransform(
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
 * Un article, en plein écran.
 *
 * **Défilable verticalement**, et ce n'est pas un ornement : SPECS.md §7.1
 * demande que l'application reste utilisable à taille de police augmentée, et
 * un extrait de 900 caractères dépasse alors l'écran. Sans ce défilement, la
 * fin du texte serait inaccessible — coupée, sans que rien ne le dise.
 */
@Composable
private fun ArticlePage(
    article: ArticleUiModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(SwipeTestTags.page(article.id)),
    ) {
        if (article.hasIllustration) ArticleIllustration(imageUrl = article.imageUrl)

        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.swipe_article_meta, article.feedTitle, article.publishedAt.label()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(text = article.title, style = MaterialTheme.typography.headlineSmall)

            if (article.excerpt.isNotBlank()) {
                Text(
                    text = article.excerpt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OpenAction(article = article, onOpen = onOpen)
        }
    }
}

/**
 * Ce qui ouvre l'article d'origine (SPECS.md §4.7).
 *
 * Un bouton explicite plutôt qu'un écran entièrement cliquable : en plein écran
 * la surface tactile est aussi celle du balayage, et un appui interprété comme
 * une ouverture ferait partir dans le navigateur au moindre geste hésitant. Un
 * article sans lien exploitable le **donne à voir**, comme en mode Liste.
 */
@Composable
private fun OpenAction(
    article: ArticleUiModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!article.isOpenable) {
        Text(
            text = stringResource(R.string.swipe_article_no_link),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.testTag(SwipeTestTags.NO_LINK),
        )
        return
    }

    Button(
        onClick = onOpen,
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .testTag(SwipeTestTags.OPEN),
    ) {
        Text(stringResource(R.string.swipe_open_article))
    }
}

/**
 * L'illustration de l'article.
 *
 * **Décorative, sans description** (SPECS.md §7.1) : le flux ne fournit aucun
 * texte alternatif, et une description forgée sur place ajouterait un nœud à
 * parcourir sans rien apprendre. Un échec de chargement **referme le créneau**
 * plutôt que d'y laisser un cadre teinté — une image qu'on ne peut pas obtenir
 * ne se distingue en rien, pour le lecteur, d'un article qui n'en a pas.
 */
@Composable
private fun ArticleIllustration(imageUrl: String?, modifier: Modifier = Modifier) {
    val painter = rememberAsyncImagePainter(model = imageUrl, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()

    if (imageUrl == null || state is AsyncImagePainter.State.Error) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ILLUSTRATION_PLACEHOLDER_ALPHA))
            .testTag(SwipeTestTags.ILLUSTRATION),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * L'écran qui suit le dernier article chargé (GOAL-012-T03).
 *
 * La fin du flux y est **dite**, jamais seulement subie : un balayage qui cesse
 * de répondre est indistinguable d'une panne (SPECS.md §4.4).
 */
@Composable
private fun TrailingPage(
    uiState: SwipeUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val phase = uiState.phase) {
            DiscoverPhase.EndOfFeed -> EndOfFeedMessage()

            /*
             * Hors ligne, le bandeau a déjà dit la cause en tête : la répéter
             * en rouge ferait de deux signaux une alarme, alors que ce qui est
             * affiché fonctionne (SPECS.md §5.2). Seule la reprise demeure.
             */
            is DiscoverPhase.Failed ->
                if (uiState.showsOfflineBanner) {
                    RetryAction(onRetry)
                } else {
                    FailureBlock(failure = phase.failure, onRetry = onRetry)
                }

            // Le flux continue, ou la session s'achève : dans les deux cas
            // cette page est une attente, et elle le montre.
            else -> LoadingIndicator()
        }
    }
}

@Composable
private fun EndOfFeedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(Spacing.xl)
            .testTag(SwipeTestTags.END_OF_FEED),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.swipe_end_of_feed_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.swipe_end_of_feed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Demande la page suivante **avant** d'atteindre le dernier article
 * (GOAL-012-T02).
 *
 * `derivedStateOf` évite de relancer l'effet à chaque pixel parcouru pendant le
 * geste : seul le passage du seuil compte. Le nombre d'articles fait aussi
 * partie de la clé — une page plus courte que le seuil laisserait sinon la
 * condition vraie sans jamais rappeler le chargement, et le flux s'arrêterait
 * sans le dire.
 */
@Composable
private fun PrefetchNextPage(
    pagerState: PagerState,
    articleCount: Int,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(pagerState, articleCount) {
        derivedStateOf { pagerState.currentPage >= articleCount - PREFETCH_DISTANCE }
    }

    LaunchedEffect(shouldLoadMore, articleCount) {
        if (shouldLoadMore) onLoadMore()
    }
}

/**
 * Relève périodiquement la visibilité de l'article affiché (GOAL-012-T01).
 *
 * **Périodiquement, et non à chaque geste.** C'est le point où ce mode se
 * distingue le plus du mode Liste : un article plein écran immobile ne produit
 * strictement aucun événement — pas même un pixel de défilement — et une mesure
 * déclenchée par le seul mouvement ne le signalerait donc jamais. La cadence
 * est celle du mode Liste, [VISIBILITY_SAMPLING_PERIOD_MILLIS], reprise avec sa
 * justification : 5 Hz situe le franchissement du seuil d'une seconde à 20 %
 * près sans réveiller l'appareil pour rien.
 *
 * **`repeatOnLifecycle(RESUMED)` et non un simple `LaunchedEffect`.** Une
 * boucle liée à la seule composition continuerait de tourner écran éteint : en
 * plein écran, elle marquerait comme lu l'article resté affiché — un faux
 * positif irréversible, puisque le marquage part ensuite au serveur.
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

/**
 * Le régime hors ligne, dit calmement (SPECS.md §5.2).
 *
 * `surfaceVariant` et non `errorContainer` : ce que l'utilisateur a sous les
 * yeux fonctionne, et le peindre aux couleurs de l'erreur laisserait croire à
 * une panne de l'application.
 */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.swipe_offline_banner),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/** L'ouverture refusée faute de réseau (SPECS.md §5.2). */
@Composable
private fun OfflineOpenNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    FeedNotice(
        message = stringResource(R.string.swipe_offline_open_blocked),
        actionLabel = stringResource(R.string.swipe_offline_notice_dismiss),
        onAction = onDismiss,
        modifier = modifier.testTag(SwipeTestTags.OFFLINE_NOTICE),
        actionModifier = Modifier.testTag(SwipeTestTags.OFFLINE_NOTICE_DISMISS),
    )
}

@Composable
private fun FailureBlock(
    failure: DiscoverFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md)
            .testTag(SwipeTestTags.FAILURE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        RetryAction(onRetry)
    }
}

@Composable
private fun RetryAction(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onRetry,
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .testTag(SwipeTestTags.RETRY),
    ) {
        Text(stringResource(R.string.swipe_retry))
    }
}

@Composable
private fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(Spacing.xl)
            .testTag(SwipeTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.swipe_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.swipe_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Preview(showBackground = true)
@Composable
private fun SwipeScreenPreview() {
    AppTheme(dynamicColor = false) {
        SwipeScreen(
            uiState = SwipeUiState(
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
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SwipeScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        SwipeScreen(
            uiState = SwipeUiState(phase = DiscoverPhase.EndOfFeed),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
        )
    }
}
