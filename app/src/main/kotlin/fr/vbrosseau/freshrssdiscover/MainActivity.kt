package fr.vbrosseau.freshrssdiscover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.SessionGate
import fr.vbrosseau.freshrssdiscover.presentation.SessionGateViewModel
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginScreen
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginViewModel
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
                /*
                 * `Surface` à la racine, et ce n'est pas décoratif : il est le
                 * seul à installer `LocalContentColor`. Sans lui, tout texte qui
                 * ne fixe pas sa couleur retombe sur du **noir**, invisible en
                 * thème sombre — c'est ce qui est arrivé au titre de l'écran de
                 * connexion, servi hors de tout `Scaffold`.
                 *
                 * Le défaut avait été rencontré en Phase 0 sur les captures
                 * Roborazzi, et « corrigé » là où il se voyait : dans le harnais
                 * de capture. Les images sont alors redevenues correctes pendant
                 * que l'application, elle, restait fautive — le harnais peignait
                 * un `Surface` que la production n'avait pas. Une capture ne vaut
                 * que si elle rend ce que l'application rend.
                 */
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

/**
 * Aiguillage racine : connexion, ou application.
 *
 * Il est piloté par la seule présence d'une session. C'est ce qui ramène
 * l'utilisateur à l'écran de connexion lorsque le serveur refuse le jeton
 * (SPECS.md §3.4), sans qu'aucun écran ait à s'en préoccuper.
 */
@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val viewModel: SessionGateViewModel = hiltViewModel()
    val gate by viewModel.gate.collectAsStateWithLifecycle()

    when (gate) {
        // La session vit sur disque : afficher l'écran de connexion pendant sa
        // première lecture le ferait clignoter à chaque lancement.
        SessionGate.Unknown -> Box(
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }

        /*
         * `safeDrawingPadding` ici, et pas dans le `Scaffold` du cas connecté :
         * lui gère déjà ses encarts. L'écran de connexion, servi nu sous
         * `enableEdgeToEdge`, passait sinon **sous la barre d'état** — son titre
         * était chevauché par l'heure.
         *
         * Aucune capture Roborazzi ne pouvait le voir : elles rendent le
         * Composable isolé, sans barres système. Seule une exécution sur
         * appareil l'a montré.
         */
        SessionGate.SignedOut -> LoginRoute(modifier = modifier.safeDrawingPadding())

        SessionGate.SignedIn -> SignedInScaffold(modifier = modifier)
    }
}

@Composable
private fun LoginRoute(modifier: Modifier = Modifier) {
    val viewModel: LoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Aucune navigation à déclencher après un succès : la session apparaît, et
    // l'aiguillage racine bascule de lui-même.
    LoginScreen(
        uiState = uiState,
        onServerAddressChange = viewModel::onServerAddressChange,
        onUsernameChange = viewModel::onUsernameChange,
        onApiPasswordChange = viewModel::onApiPasswordChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

/**
 * Ossature de l'application connectée : barre de titre, barre de navigation,
 * graphe.
 *
 * `TopAppBar` est encore expérimental dans Material 3 ; l'opt-in est local
 * plutôt que déclaré pour tout le module, afin que la dette reste visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInScaffold(modifier: Modifier = Modifier) {
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
