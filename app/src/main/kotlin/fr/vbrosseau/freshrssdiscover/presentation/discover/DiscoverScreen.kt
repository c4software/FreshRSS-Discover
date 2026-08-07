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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import fr.vbrosseau.freshrssdiscover.R
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
 */
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val phase = uiState.phase

    when {
        uiState.articles.isNotEmpty() -> ArticleList(
            uiState = uiState,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onArticleClick = onArticleClick,
            modifier = modifier,
            listState = listState,
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

@Composable
private fun ArticleList(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    PrefetchNextPage(
        listState = listState,
        articleCount = uiState.articles.size,
        onLoadMore = onLoadMore,
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag(DiscoverTestTags.LIST),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Clé stable : sans elle, l'insertion d'articles en tête (SPECS.md
        // §4.6) recomposerait toute la liste et déplacerait la lecture en cours.
        items(items = uiState.articles, key = ArticleUiModel::id) { article ->
            ArticleCard(article = article, onClick = { onArticleClick(article.id) })
        }

        item(key = FOOTER_KEY) {
            FeedFooter(phase = uiState.phase, onRetry = onRetry)
        }
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
    phase: DiscoverPhase,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            is DiscoverPhase.Failed -> FailureBlock(failure = phase.failure, onRetry = onRetry)

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
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .testTag(DiscoverTestTags.RETRY),
        ) {
            Text(stringResource(R.string.discover_retry))
        }
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
