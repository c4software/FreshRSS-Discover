package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vbrosseau.freshrssdiscover.presentation.browser.rememberArticleOpener
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.ReadingPositionViewModel
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsScreen
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsViewModel

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
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DISCOVER,
        modifier = modifier,
    ) {
        composable(AppRoutes.DISCOVER) {
            DiscoverRoute()
        }

        composable(AppRoutes.SETTINGS) {
            SettingsRoute()
        }
    }
}

@Composable
private fun DiscoverRoute(modifier: Modifier = Modifier) {
    val viewModel: DiscoverViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // L'ouvreur est construit ici, sous `AppTheme` : il en lit la couleur de
    // barre, et a besoin du `Context` de l'Activity pour que l'onglet reste
    // dans la pile de l'application — sinon le retour ne ramènerait pas au flux.
    val articleOpener = rememberArticleOpener()

    // ViewModel distinct : la position de lecture a son propre cycle, elle
    // survit aux rechargements et aux rafraîchissements du flux.
    val positionViewModel: ReadingPositionViewModel = hiltViewModel()
    val restoreToArticleId by positionViewModel.restoreToArticleId.collectAsStateWithLifecycle()

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
        onRefresh = viewModel::refresh,
        onOfflineNoticeDismiss = viewModel::dismissOfflineOpenNotice,
        onFirstVisibleArticleChanged = positionViewModel::onFirstVisibleArticleChanged,
        onPositionRestored = positionViewModel::onPositionRestored,
        restoreToArticleId = restoreToArticleId,
        // Sans ce rappel, la mesure de visibilité ne s'arme pas : `null` signifie
        // « personne n'écoute », et le marquage automatique resterait inerte.
        onVisibilityChanged = viewModel::onVisibilityChanged,
        modifier = modifier,
    )
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
        modifier = modifier,
    )
}
