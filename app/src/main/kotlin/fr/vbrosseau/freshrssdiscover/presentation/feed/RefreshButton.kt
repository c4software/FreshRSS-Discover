package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R

/** Cible tactile minimale (SPECS.md §7.1) : Material s'arrête à 40 dp. */
private val MinTouchTarget = 48.dp

/** Diamètre de l'indicateur qui remplace l'icône : celui de l'icône. */
private val IndicatorSize = 24.dp

/**
 * Épaisseur du trait de l'indicateur.
 *
 * Celle par défaut est calibrée pour un indicateur de 40 dp ; à 24 dp elle
 * donne un anneau épais qui se lit comme un disque plein.
 */
private val IndicatorStroke = 2.dp

/**
 * Recharger le flux, sans avoir à le tirer (SPECS.md §4.6).
 *
 * Un seul composant pour les deux modes de présentation, et c'est le point :
 * l'action est la même — vider, recharger, revenir au début — et deux
 * implémentations divergeraient au premier ajustement fait d'un seul côté.
 *
 * **Pourquoi un bouton alors que le mode Liste a déjà son geste.** En plein
 * écran il n'y a pas de liste à tirer, et superposer un tirage vertical au
 * balayage horizontal donnerait deux gestes concurrents sur la même surface.
 * Le bouton est donc nécessaire là ; il sert aussi le mode Liste **en plus** du
 * geste, parce qu'un tirage n'est pas praticable par tout le monde
 * (SPECS.md §7.1) et qu'aucune commande ne le remplaçait.
 *
 * **Il est posé sur la ligne du titre**, et non superposé au contenu : c'est
 * une commande qui porte sur l'écran entier, et posée par-dessus le flux elle
 * en recouvrait toujours une part — le coin de la première carte en mode Liste,
 * l'illustration en mode Balayage. Un `IconButton` nu convient dès lors : la
 * barre lui fournit un fond uni, là où un fond plein était nécessaire pour
 * rester lisible sur une image quelconque.
 *
 * **Il se change en indicateur pendant le rechargement**, plutôt que de se
 * griser ou de disparaître. Un bouton grisé dit « indisponible » et non « en
 * cours » ; un bouton qui disparaît laisse un trou, et l'utilisateur ne sait
 * plus si son appui a été pris. Il reste par ailleurs inerte tant que le
 * rechargement dure : `onClick` n'est plus branché, ce qui rend le double appui
 * sans effet ici même, en plus de la garde du ViewModel.
 */
@Composable
fun RefreshButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { if (!isRefreshing) onRefresh() },
        modifier = modifier
            .size(MinTouchTarget)
            .testTag(RefreshTestTags.BUTTON),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(IndicatorSize),
                strokeWidth = IndicatorStroke,
                color = LocalContentColor.current,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.feed_refresh),
            )
        }
    }
}
