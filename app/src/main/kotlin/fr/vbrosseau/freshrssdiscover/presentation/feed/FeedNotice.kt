package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Cible tactile minimale (SPECS.md §7.1). */
private val MinTouchTarget = 48.dp

/**
 * Une bandelette posée sur le flux, qui ne l'interrompt pas.
 *
 * **Elle s'acquitte à la main, jamais par minuteur.** Un message qui s'efface
 * tout seul se rate, et ceux du flux expliquent tous quelque chose qu'on n'a
 * pas vu venir — une ouverture refusée, un flux qui date. C'est le choix déjà
 * fait pour l'avis hors ligne, et il vaut pour tous.
 *
 * La couleur des actions vient de `SnackbarDefaults` : un `TextButton`
 * ordinaire peindrait son libellé en `primary`, couleur pensée pour la surface
 * du fond et non pour celle, inversée, de la bandelette.
 *
 * Les repères de test restent **par écran** et arrivent par
 * [actionModifier]/[dismissModifier] : les deux modes ont les leurs, et les
 * absorber ici les confondrait dans les tests d'écran.
 *
 * @param dismissLabel seconde commande, facultative. Un avis dont la seule
 *   action est de réparer la situation doit pouvoir se taire quand même :
 *   l'utilisateur hors d'état de la réparer n'a sinon aucune issue.
 */
@Composable
fun FeedNotice(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissModifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier.padding(Spacing.md),
        action = {
            NoticeAction(label = actionLabel, onClick = onAction, modifier = actionModifier)
        },
        dismissAction = if (dismissLabel != null && onDismiss != null) {
            { NoticeAction(label = dismissLabel, onClick = onDismiss, modifier = dismissModifier) }
        } else {
            null
        },
    ) {
        Text(message)
    }
}

@Composable
private fun NoticeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionContentColor),
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        Text(label)
    }
}
