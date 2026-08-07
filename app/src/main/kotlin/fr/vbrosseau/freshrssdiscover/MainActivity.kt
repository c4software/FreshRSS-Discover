package fr.vbrosseau.freshrssdiscover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppDestination
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppNavHost
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppNavigationBar
import fr.vbrosseau.freshrssdiscover.presentation.navigation.navigateToTopLevel
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}

/**
 * Ossature commune : barre de titre, barre de navigation, graphe.
 *
 * `TopAppBar` est encore expérimental dans Material 3 ; l'opt-in est local
 * plutôt que déclaré pour tout le module, afin que la dette reste visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = AppDestination.forRoute(currentRoute)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(currentDestination?.labelRes ?: R.string.app_name))
                },
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentRoute = currentRoute,
                onSelect = navController::navigateToTopLevel,
            )
        },
    ) { innerPadding ->
        AppNavHost(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
        )
    }
}
