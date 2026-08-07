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
import fr.vbrosseau.freshrssdiscover.presentation.PlaceholderScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel

/**
 * Graphe de navigation.
 *
 * Chaque destination récupère son ViewModel ici (`hiltViewModel()`) et
 * transmet un état à un écran sans état : c'est ce qui garde les écrans
 * prévisualisables et testables sans injection. Les réglages affichent encore
 * [PlaceholderScreen] — voir TASKS.md pour le Goal qui les livre.
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
            PlaceholderScreen()
        }
    }
}

@Composable
private fun DiscoverRoute(modifier: Modifier = Modifier) {
    val viewModel: DiscoverViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DiscoverScreen(
        uiState = uiState,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        // TODO(GOAL-010) : ouvrir le lien d'origine dans un onglet personnalisé
        //  (SPECS.md §4.7). L'écran expose déjà le geste et distingue les
        //  articles sans lien ; il ne manque que l'ouverture elle-même.
        onArticleClick = {},
        modifier = modifier,
    )
}
