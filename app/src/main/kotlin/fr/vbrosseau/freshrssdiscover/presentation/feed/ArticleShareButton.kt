package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R

/** Cible tactile minimale (SPECS.md §7.1) : Material s'arrête à 40 dp. */
private val MinTouchTarget = 48.dp

/**
 * Partager le lien de l'article (SPECS.md §4.3).
 *
 * Un seul composant pour les deux modes, comme [RefreshButton] et pour la même
 * raison : c'est la même action, et deux implémentations divergeraient au
 * premier ajustement fait d'un seul côté.
 *
 * **L'appelant ne le pose pas quand l'article n'a pas de lien.** Le bouton ne
 * décide pas de sa propre absence : il n'y a rien à annoncer à un lecteur
 * d'écran, et une commande grisée dirait « pas maintenant » là où la réponse
 * est « jamais pour cet article ».
 *
 * **Une icône seule, avec sa description.** Le libellé serait redondant sur une
 * carte qui n'a qu'une commande, et en mode Liste il prendrait la place d'une
 * ligne de texte sur chaque article. La description est ce qui rend la commande
 * annonçable (SPECS.md §7.1) ; `size` la porte à 48 dp, Material s'arrêtant à
 * 40.
 *
 * @param testTag propre à l'article, comme pour [ArticleIllustration] : en mode
 *   Liste il y a autant de boutons que de cartes affichées, et un repère
 *   partagé ne désignerait rien.
 */
@Composable
fun ArticleShareButton(
    onShare: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onShare,
        modifier = modifier
            .size(MinTouchTarget)
            .testTag(testTag),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_share),
            contentDescription = stringResource(R.string.feed_article_share),
        )
    }
}
