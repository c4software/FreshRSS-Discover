package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPosition
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * Nombre d'articles restants sous lesquels la page suivante est demandée.
 *
 * SPECS.md §4.4 veut que le défilement ne s'interrompe pas : attendre le
 * dernier élément visible ferait apparaître l'indicateur à chaque fois. Cinq —
 * un huitième d'une page de 40 — laisse le temps d'un aller-retour réseau au
 * rythme de défilement ordinaire.
 */
private const val PREFETCH_DISTANCE = 5

/**
 * Rapport largeur/hauteur du créneau d'illustration.
 *
 * Le créneau est dimensionné par ce rapport, **jamais** par la taille de
 * l'image : une hauteur déduite de l'image reçue changerait au moment où elle
 * arrive, et la liste sursauterait sous le doigt. 16/9 est le format des
 * bandeaux d'articles les plus courants, donc celui qui rogne le moins.
 */
private const val ILLUSTRATION_ASPECT_RATIO = 16f / 9f

/**
 * Opacité de la teinte qui marque le créneau pendant le chargement.
 *
 * Elle s'applique à `onSurface`, c'est-à-dire à la couleur *opposée* au fond de
 * la carte : elle assombrit en thème clair et éclaircit en thème sombre. C'est
 * ce qui corrige le défaut constaté — `surfaceVariant` est presque confondu
 * avec le conteneur de la carte en thème clair, alors qu'il s'en détache en
 * sombre, et le réservé y était donc invisible.
 */
private const val ILLUSTRATION_PLACEHOLDER_ALPHA = 0.12f

/** Cible tactile minimale (SPECS.md §7.1) : Material s'arrête à 40 dp. */
private val MinTouchTarget = 48.dp

/**
 * Le flux Discover.
 *
 * Sans état métier : il affiche [uiState] et remonte les gestes, ce qui le rend
 * prévisualisable et testable sans graphe d'injection (AGENTS.md §9). Le seul
 * état qu'il tient est la position de défilement, qui n'appartient qu'à lui.
 *
 * @param onVisibilityChanged destinataire des relevés de visibilité (SPECS.md
 *   §4.5). **Nullable, et nul par défaut** : l'observation est une boucle
 *   périodique, et l'armer sans destinataire ferait tourner un minuteur pour
 *   jeter son résultat — c'est-à-dire dépenser de la batterie pour rien, et
 *   rendre les prévisualisations et les tests de rendu perpétuellement occupés.
 *   `null` dit donc « personne n'écoute », ce qu'un `{}` par défaut ne saurait
 *   exprimer.
 */
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onOfflineNoticeDismiss: () -> Unit = {},
    onFirstVisibleArticleChanged: (ArticleId?, Long) -> Unit = { _, _ -> },
    onPositionRestored: () -> Unit = {},
    positionToRestore: ReadingPosition? = null,
    listState: LazyListState = rememberLazyListState(),
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    RestoreReadingPosition(
        listState = listState,
        articles = uiState.articles,
        positionToRestore = positionToRestore,
        onPositionRestored = onPositionRestored,
    )
    RememberReadingPosition(
        listState = listState,
        articles = uiState.articles,
        onFirstVisibleArticleChanged = onFirstVisibleArticleChanged,
    )
    ScrollToTopAfterRefresh(listState = listState, isRefreshing = uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Au-dessus du flux et non par-dessus : le bandeau informe, il ne
            // masque rien de ce qui reste lisible (SPECS.md §5.2).
            if (uiState.showsOfflineBanner) OfflineBanner()

            FeedBody(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onRefresh = onRefresh,
                onArticleClick = onArticleClick,
                modifier = Modifier.weight(1f),
                listState = listState,
                onVisibilityChanged = onVisibilityChanged,
            )
        }

        if (uiState.isOfflineOpenNoticeVisible) {
            OfflineOpenNotice(
                onDismiss = onOfflineNoticeDismiss,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun FeedBody(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
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
            modifier = modifier,
            listState = listState,
            onVisibilityChanged = onVisibilityChanged,
        )

        /*
         * Une session terminée est traitée comme une attente, et non comme une
         * erreur : le dépôt vient d'invalider le jeton, l'aiguillage racine
         * bascule de lui-même vers la connexion (SPECS.md §3.4). Afficher un
         * message ici reviendrait à commenter un écran sur le point de
         * disparaître.
         */
        phase == DiscoverPhase.InitialLoading ||
            phase == DiscoverPhase.SessionEnded -> Centered(modifier) { LoadingIndicator() }

        phase is DiscoverPhase.Failed -> Centered(modifier) {
            FailureBlock(failure = phase.failure, onRetry = onRetry)
        }

        else -> Centered(modifier) { EmptyFeedMessage() }
    }
}

/**
 * Le flux et son geste de rafraîchissement (SPECS.md §4.6).
 *
 * Le tirage n'est armé qu'ici, sur la liste : les états sans article ont déjà
 * leur reprise — « Réessayer » — et un geste de défilement sur un écran qui
 * n'en propose pas ne se découvre pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleList(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    PrefetchNextPage(
        listState = listState,
        articleCount = uiState.articles.size,
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
             * L'indicateur par défaut peint son disque en `surfaceContainer` :
             * posé sur une carte d'article, il s'y confond en thème clair, et
             * la capture l'a montré effaçant le titre qu'il recouvre. Le
             * conteneur primaire le détache des deux fonds, et son arc en
             * `onPrimaryContainer` reste lisible dans les deux thèmes.
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
            // Clé stable : sans elle, l'insertion d'articles en tête (SPECS.md
            // §4.6) recomposerait toute la liste et déplacerait la lecture en
            // cours. Avec elle, la liste repositionne son premier élément
            // visible sur sa **clé** et non sur son rang : les articles insérés
            // au-dessus ne poussent donc pas la lecture vers le bas.
            items(items = uiState.articles, key = ArticleUiModel::id) { article ->
                ArticleCard(article = article, onClick = { onArticleClick(article.id) })
            }

            item(key = FOOTER_KEY) {
                FeedFooter(uiState = uiState, onRetry = onRetry)
            }
        }
    }
}

/**
 * Le régime hors ligne, dit calmement (SPECS.md §5.2).
 *
 * `surfaceVariant` et non `errorContainer` : ce que l'utilisateur a sous les
 * yeux fonctionne, et le peindre aux couleurs de l'erreur laisserait croire à
 * une panne de l'application. La paire `surfaceVariant`/`onSurfaceVariant` est
 * définie dans les deux thèmes, ce qu'une couleur choisie à la main ne
 * garantirait pas.
 */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .testTag(DiscoverTestTags.OFFLINE_BANNER),
    ) {
        Text(
            text = stringResource(R.string.discover_offline_banner),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/**
 * L'ouverture refusée faute de réseau (SPECS.md §5.2).
 *
 * Une bandelette posée sur le flux, qui ne l'interrompt pas : le geste a
 * échoué, la lecture continue. Elle est **acquittée à la main** plutôt que
 * retirée par un minuteur — un message qui s'efface tout seul se rate, et
 * celui-ci explique pourquoi rien ne s'est passé.
 *
 * La couleur de l'action vient de `SnackbarDefaults` : un `TextButton` ordinaire
 * peindrait son libellé en `primary`, couleur pensée pour la surface du fond et
 * non pour celle, inversée, de la bandelette.
 */
@Composable
private fun OfflineOpenNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Snackbar(
        modifier = modifier
            .padding(Spacing.md)
            .testTag(DiscoverTestTags.OFFLINE_NOTICE),
        action = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionContentColor),
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag(DiscoverTestTags.OFFLINE_NOTICE_DISMISS),
            ) {
                Text(stringResource(R.string.discover_offline_notice_dismiss))
            }
        },
    ) {
        Text(stringResource(R.string.discover_offline_open_blocked))
    }
}

/**
 * Demande la page suivante **avant** d'atteindre le bas (SPECS.md §4.4).
 *
 * `derivedStateOf` évite de relancer l'effet à chaque pixel parcouru : seul le
 * passage du seuil compte. Le nombre d'articles fait aussi partie de la clé —
 * une page plus courte que le seuil laisserait sinon la condition vraie sans
 * jamais rappeler le chargement, et le flux s'arrêterait sans le dire.
 */
@Composable
private fun PrefetchNextPage(
    listState: LazyListState,
    articleCount: Int,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(listState, articleCount) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && lastVisible >= articleCount - PREFETCH_DISTANCE
        }
    }

    LaunchedEffect(shouldLoadMore, articleCount) {
        if (shouldLoadMore) onLoadMore()
    }
}

/**
 * Relève périodiquement la visibilité des articles (SPECS.md §4.5).
 *
 * **Périodiquement, et non à chaque défilement.** Le seuil porte sur une durée
 * continue : un article immobile à l'écran ne produit aucun événement, et une
 * mesure déclenchée par le seul défilement ne le signalerait donc jamais. La
 * cadence retenue et sa justification sont dans
 * [VISIBILITY_SAMPLING_PERIOD_MILLIS].
 *
 * **`repeatOnLifecycle(RESUMED)` et non un simple `LaunchedEffect`.** Une
 * boucle liée à la seule composition continuerait de tourner écran éteint ou
 * application en arrière-plan : elle marquerait comme lus des articles que
 * personne ne regarde — un faux positif irréversible, puisque le marquage part
 * ensuite au serveur — tout en réveillant l'appareil cinq fois par seconde.
 * `RESUMED` plutôt que `STARTED` : c'est le seul état où l'écran est réellement
 * au premier plan et non simplement visible derrière une boîte de dialogue.
 *
 * Le calcul vit dans un effet, jamais dans le corps d'un Composable
 * (AGENTS.md §9) : il lit une disposition, il ne produit pas d'affichage.
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
 * Une carte par article.
 *
 * La carte n'est cliquable que si l'article a un lien : SPECS.md §4.7 demande
 * qu'un article sans lien exploitable ne le soit pas, et le donne à voir — d'où
 * la mention explicite plutôt qu'un simple clic sans effet.
 */
@Composable
private fun ArticleCard(
    article: ArticleUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchTarget)
        .testTag(DiscoverTestTags.card(article.id))

    if (article.isOpenable) {
        Card(onClick = onClick, modifier = cardModifier) { ArticleCardContent(article) }
    } else {
        Card(modifier = cardModifier) { ArticleCardContent(article) }
    }
}

@Composable
private fun ArticleCardContent(article: ArticleUiModel) {
    if (article.hasIllustration) {
        ArticleIllustration(imageUrl = article.imageUrl)
    }

    Column(
        modifier = Modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
        )

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
}

/**
 * L'illustration de l'article.
 *
 * **Décorative, sans description** (SPECS.md §7.1, qui laisse le choix entre une
 * description et un marquage explicitement décoratif). Le bandeau d'un article
 * de flux n'apporte quasiment jamais d'information que le titre ne porte pas
 * déjà ; nous n'avons d'ailleurs aucun texte alternatif à en donner — le flux
 * n'en fournit pas — et une description forgée sur place (« image de
 * l'article ») ajouterait un nœud à parcourir sans rien apprendre. Un
 * `contentDescription` nul est la façon dont Compose déclare cela : l'image ne
 * produit alors aucun nœud sémantique, et le lecteur d'écran lit la carte comme
 * s'il n'y avait que le texte.
 *
 * **Un échec de chargement referme le créneau** plutôt que d'y laisser un cadre
 * teinté : SPECS.md §4.3 proscrit l'espace vide et l'image de remplacement
 * générique, et une image qu'on ne peut pas obtenir ne se distingue en rien,
 * pour le lecteur, d'un article qui n'en a pas. Une URL absente est traitée de
 * la même façon, faute d'avoir quoi que ce soit à charger.
 *
 * **Le créneau garde la même hauteur avant et après le chargement** : elle vient
 * du rapport d'aspect et de la largeur, jamais de l'image reçue. Sans cela la
 * liste se décalerait à chaque image arrivée — le pire défaut possible dans une
 * application dont tout l'usage est le défilement.
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
            // Peinte sous l'image, cette teinte n'est visible que tant qu'il n'y
            // a rien à montrer : elle dit que la place est réservée, sans
            // prétendre être une illustration.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ILLUSTRATION_PLACEHOLDER_ALPHA))
            .testTag(DiscoverTestTags.ILLUSTRATION),
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
 * Ce qui suit le dernier article.
 *
 * La fin du flux y est **dite**, jamais seulement subie : une liste qui cesse
 * de s'allonger est indistinguable d'une panne (SPECS.md §4.4).
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

            DiscoverPhase.EndOfFeed -> Text(
                text = stringResource(R.string.discover_end_of_feed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .testTag(DiscoverTestTags.END_OF_FEED),
            )

            /*
             * Hors ligne, le bandeau a déjà dit la cause en tête du flux : la
             * répéter en rouge sous le dernier article ferait de deux signaux
             * une alarme, alors que ce qui est affiché fonctionne (SPECS.md
             * §5.2). Seule la reprise demeure — c'est elle que SPECS.md §4.4
             * exige, pas la couleur.
             */
            is DiscoverPhase.Failed ->
                if (uiState.showsOfflineBanner) {
                    RetryAction(onRetry)
                } else {
                    FailureBlock(failure = phase.failure, onRetry = onRetry)
                }

            // Rien à dire : le flux continue, ou la session s'achève.
            DiscoverPhase.Idle,
            DiscoverPhase.InitialLoading,
            DiscoverPhase.SessionEnded,
            -> Unit
        }
    }
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
            .testTag(DiscoverTestTags.FAILURE),
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
            .testTag(DiscoverTestTags.RETRY),
    ) {
        Text(stringResource(R.string.discover_retry))
    }
}

@Composable
private fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(Spacing.xl)
            .testTag(DiscoverTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.discover_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.discover_empty_body),
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

/** Clé du pied de liste, distincte de tout identifiant d'article. */
private const val FOOTER_KEY = "discover:footer"

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
        )
    }
}

/** Le régime hors ligne : bandeau calme, cache intact, avis d'ouverture refusée. */
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
        )
    }
}

/**
 * Remonte en haut à la fin d'un tirer-pour-rafraîchir.
 *
 * SPECS.md §4.6 : le geste vide la liste et repart du début. Sans cette
 * remontée, l'utilisateur resterait à un rang qui ne désigne plus rien de ce
 * qu'il regardait — le contenu a été remplacé sous lui.
 *
 * Déclenché sur la **retombée** de l'indicateur, pas sur sa montée : remonter
 * avant que la nouvelle liste ne soit posée ferait défiler l'ancienne.
 */
@Composable
private fun ScrollToTopAfterRefresh(listState: LazyListState, isRefreshing: Boolean) {
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) listState.scrollToItem(0)
        wasRefreshing = isRefreshing
    }
}

/**
 * Ramène la liste à l'article où la lecture s'était arrêtée (SPECS.md §5.3).
 *
 * `scrollToItem` et non `animateScrollToItem` : il s'agit de restituer un état,
 * pas de montrer un mouvement que l'utilisateur n'a pas demandé.
 *
 * Si l'article n'est plus là — purgé, ou devenu lu et sorti du flux — rien ne se
 * produit et la liste reste en haut. La position est acquittée dans tous les
 * cas : la garder en attente ferait sauter la liste au prochain chargement.
 */
@Composable
private fun RestoreReadingPosition(
    listState: LazyListState,
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
            listState.scrollToItem(index)
            onPositionRestored()
        }
    }
}

/**
 * Signale l'article en tête d'écran, pour que la lecture puisse reprendre là.
 *
 * Sa **date** part avec lui : l'article exact aura presque toujours disparu du
 * flux à la réouverture — c'est celui que le marquage vient de rendre lu — et
 * seule la date permet alors la reprise au plus proche (SPECS.md §5.3).
 */
@Composable
private fun RememberReadingPosition(
    listState: LazyListState,
    articles: List<ArticleUiModel>,
    onFirstVisibleArticleChanged: (ArticleId?, Long) -> Unit,
) {
    val firstVisible by remember(listState, articles) {
        derivedStateOf {
            val key = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
            articles.firstOrNull { it.id == key }
        }
    }

    LaunchedEffect(firstVisible) {
        firstVisible?.let { onFirstVisibleArticleChanged(ArticleId(it.id), it.publishedAtEpochSeconds) }
    }
}
