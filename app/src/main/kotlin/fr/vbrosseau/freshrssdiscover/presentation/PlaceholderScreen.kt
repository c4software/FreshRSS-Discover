package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * Écran d'attente d'une destination pas encore livrée.
 *
 * Il existe pour que l'ossature — thème, barre de titre, barre de navigation,
 * graphe — soit exécutable et vérifiable dès la Phase 0, sans anticiper le
 * contenu des écrans réels. Chaque appel disparaît avec le Goal qui livre
 * l'écran correspondant : un `PlaceholderScreen` encore présent est une dette
 * visible, et [TASKS.md](../../../../../../../TASKS.md) la recense.
 */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl)
            .testTag(PlaceholderTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.placeholder_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    AppTheme(dynamicColor = false) {
        PlaceholderScreen()
    }
}
