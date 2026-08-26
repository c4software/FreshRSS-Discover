package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController

/**
 * Navigation bar between top-level destinations.
 *
 * Driven by the [AppDestination] enum: adding a destination requires no
 * change here.
 *
 * @param onReselect tap on the already selected item. Split from [onSelect]
 *   because the two gestures mean different things: one is a move, the other
 *   a "bring me back to the start" on the destination already shown. Routing
 *   both through [onSelect] would force every caller to re-derive what the
 *   bar already knows.
 * @param transparent no container: over the immersive feed the page runs
 *   under the bar and paints the fade the items stand on (SPECS.md §4.8).
 */
@Composable
fun AppNavigationBar(
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: (AppDestination) -> Unit = {},
    transparent: Boolean = false,
) {
    val current = AppDestination.forRoute(currentRoute)

    NavigationBar(
        modifier = modifier,
        containerColor = if (transparent) Color.Transparent else NavigationBarDefaults.containerColor,
    ) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = {
                    if (destination == current) onReselect(destination) else onSelect(destination)
                },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.shortLabelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.testTag(NavigationTestTags.item(destination)),
            )
        }
    }
}

/**
 * Navigates to a bar destination.
 *
 * `launchSingleTop` and popping to the root prevent unbounded stacking: without
 * them, ten round trips would produce ten back-stack entries.
 */
fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
