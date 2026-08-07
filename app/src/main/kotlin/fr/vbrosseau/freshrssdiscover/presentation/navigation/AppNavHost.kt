package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vbrosseau.freshrssdiscover.presentation.PlaceholderScreen

/**
 * Graphe de navigation.
 *
 * Chaque destination récupérera son ViewModel ici (`hiltViewModel()`) et
 * transmettra un état à un écran sans état : c'est ce qui garde les écrans
 * prévisualisables et testables sans injection. Les deux destinations affichent
 * pour l'instant [PlaceholderScreen] — voir TASKS.md pour le Goal qui livre
 * chacune d'elles.
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
            PlaceholderScreen()
        }

        composable(AppRoutes.SETTINGS) {
            PlaceholderScreen()
        }
    }
}
