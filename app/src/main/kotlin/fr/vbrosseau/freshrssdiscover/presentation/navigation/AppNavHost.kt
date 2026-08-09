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
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRefresh
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsScreen
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeScreen
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel

/**
 * Graphe de navigation.
 *
 * Chaque destination récupère son ViewModel ici (`hiltViewModel()`) et
 * transmet un état à un écran sans état : c'est ce qui garde les écrans
 * prévisualisables et testables sans injection.
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
        // Une seule destination pour les deux modes, et non deux routes : le
        // flux est le même, seule sa présentation change (SPECS.md §4.8).
        // Deux routes obligeraient à naviguer sur un changement de réglage,
        // donc à décider ce que devient la pile de retour — pour un choix qui
        // n'est pas un déplacement.
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

    // L'ouvreur est construit ici, sous `AppTheme` : il en lit la couleur de
    // barre, et a besoin du `Context` de l'Activity pour que l'onglet reste
    // dans la pile de l'application — sinon le retour ne ramènerait pas au flux.
    val articleOpener = rememberArticleOpener()

    /*
     * Le partage ne passe **pas** par le ViewModel, à la différence de
     * l'ouverture : il ne marque pas l'article lu — on n'a rien lu en
     * l'envoyant — et il ne demande pas le réseau, puisque rien ne part
     * d'ici : c'est l'application choisie qui décidera quoi en faire, quand
     * elle le pourra. Il n'y a donc aucune décision à prendre en amont.
     */
    val articleSharer = rememberArticleSharer()

    AskTheServerWhenShownEmpty(viewModel::onScreenShown)

    PublishFeedRefresh(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        onFeedRefreshChange = onFeedRefreshChange,
    )

    DiscoverScreen(
        uiState = uiState,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        // Le ViewModel décide s'il y a lieu d'ouvrir : il marque l'article lu
        // et refuse hors ligne, où l'ouverture échouerait sans rien expliquer.
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
        // Sans ce rappel, la mesure de visibilité ne s'arme pas : `null` signifie
        // « personne n'écoute », et le marquage automatique resterait inerte.
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
 * Prévient le ViewModel que son écran vient au premier plan (GOAL-025).
 *
 * C'est ici, et non dans le ViewModel, que ce fait est connu : le cycle de vie
 * est une affaire de présentation, et un ViewModel qui l'observerait tiendrait
 * une référence à quelque chose qui lui survit mal.
 *
 * `LifecycleResumeEffect` et non `LaunchedEffect` : ce dernier ne se déclenche
 * qu'à l'entrée en composition, et une application revenue de veille ou de
 * l'arrière-plan ne recompose pas — le cas le plus courant serait précisément
 * celui qu'on manquerait. `RESUMED` plutôt que `STARTED`, comme
 * l'échantillonnage de visibilité : c'est le seul état où l'écran est vraiment
 * devant l'utilisateur, et non derrière une boîte de dialogue.
 *
 * Ce que le rappel décide, lui, appartient au ViewModel : l'écran dit ce qui
 * arrive, jamais ce qu'il faut en faire.
 */
@Composable
private fun AskTheServerWhenShownEmpty(onScreenShown: () -> Unit) {
    LifecycleResumeEffect(onScreenShown) {
        onScreenShown()
        onPauseOrDispose { }
    }
}

/**
 * Publie le rechargement de la destination courante vers la barre de titre.
 *
 * `DisposableEffect` et non `LaunchedEffect` : ce qui compte autant que la
 * publication, c'est le **retrait**. Sans `onDispose`, quitter le flux pour les
 * réglages y laisserait un bouton branché sur un ViewModel qu'on ne regarde
 * plus — et basculer de Liste à Balayage laisserait celui du mode précédent.
 */
@Composable
private fun PublishFeedRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFeedRefreshChange: (FeedRefresh?) -> Unit,
) {
    DisposableEffect(isRefreshing, onRefresh, onFeedRefreshChange) {
        onFeedRefreshChange(FeedRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh))
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
