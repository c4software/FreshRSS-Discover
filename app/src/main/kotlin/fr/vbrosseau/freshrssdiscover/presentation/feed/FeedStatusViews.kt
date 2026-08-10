package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.message
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Cible tactile minimale (Material) : un bouton texte seul n'y arrive pas toujours. */
private val MinTouchTarget = 48.dp

/*
 * Les états terminaux du flux, écrits une fois pour les deux modes.
 *
 * Chaque composant a existé en deux copies — une par écran — où seuls
 * changeaient le libellé et l'étiquette de test. La règle est celle
 * d'`ArticleIllustration` : deux copies divergeraient au premier correctif.
 * Ce qui reste aux écrans, c'est la **configuration** — leurs chaînes, leurs
 * étiquettes — jamais la mise en page.
 */

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
internal fun FeedOfflineBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/**
 * Le flux affiché date de plusieurs heures (SPECS.md §4.6).
 *
 * L'action **emprunte le rechargement existant** plutôt que d'en ouvrir un à
 * elle : deux chemins vers le même geste divergeraient. La seconde commande
 * existe pour qui ne veut pas recharger maintenant — sans elle, l'avis ne
 * pourrait se taire qu'en obéissant. Les chaînes sont communes aux deux modes
 * (`feed_stale_*`) ; seules les étiquettes de test viennent de l'écran.
 */
@Composable
internal fun FeedStaleNotice(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    dismissModifier: Modifier = Modifier,
) {
    FeedNotice(
        message = stringResource(R.string.feed_stale_notice),
        actionLabel = stringResource(R.string.feed_stale_refresh),
        onAction = onRefresh,
        modifier = modifier,
        actionModifier = actionModifier,
        dismissLabel = stringResource(R.string.feed_stale_dismiss),
        onDismiss = onDismiss,
        dismissModifier = dismissModifier,
    )
}

/**
 * Un chargement a échoué : le message, puis sa reprise (SPECS.md §4.4).
 */
@Composable
internal fun FeedFailureBlock(
    failure: DiscoverFailure,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        FeedRetryAction(label = retryLabel, onRetry = onRetry, modifier = retryModifier)
    }
}

/** La reprise après échec, seule — le pied de liste l'affiche sans le message hors ligne. */
@Composable
internal fun FeedRetryAction(
    label: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onRetry,
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        Text(label)
    }
}

/**
 * Le flux est au bout **sans rien à montrer** : « vous avez tout lu » sous une
 * liste vide n'expliquerait rien, d'où un message à part entière.
 */
@Composable
internal fun FeedEmptyMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Centre son contenu plein cadre : l'attente et les messages terminaux. */
@Composable
internal fun FeedCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * Exécute [onSettled] à la **retombée** du rechargement, jamais à sa montée.
 *
 * SPECS.md §4.6 : le geste vide et repart du début — l'écran doit revenir en
 * tête, mais seulement une fois la nouvelle liste posée ; y aller dès l'appui
 * ferait défiler un contenu que l'on s'apprête à jeter. Le front descendant
 * était détecté à la main dans chaque écran, à l'identique.
 */
@Composable
internal fun AfterRefreshSettles(isRefreshing: Boolean, onSettled: suspend () -> Unit) {
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) onSettled()
        wasRefreshing = isRefreshing
    }
}
