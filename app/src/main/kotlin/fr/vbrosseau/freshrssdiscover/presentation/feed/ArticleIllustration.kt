package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Rapport de forme du créneau d'illustration.
 *
 * **Fixe, jamais déduit de l'image reçue** : une hauteur qui suivrait l'image
 * changerait au moment où elle arrive, et le contenu sauterait sous le doigt.
 * 16/9 est le format des bandeaux d'articles les plus courants, donc celui qui
 * rogne le moins.
 */
private const val ILLUSTRATION_ASPECT_RATIO = 16f / 9f

/**
 * Opacité de la teinte qui marque le créneau pendant le chargement.
 *
 * Appliquée à `onSurface`, c'est-à-dire à la couleur **opposée** au fond : elle
 * assombrit en thème clair et éclaircit en thème sombre. `surfaceVariant`, lui,
 * se confond avec le fond en thème clair — un réservé d'image de contraste 1,00
 * a déjà été livré ainsi dans ce dépôt.
 */
private const val ILLUSTRATION_PLACEHOLDER_ALPHA = 0.12f

/**
 * L'illustration d'un article, partagée par les deux modes (SPECS.md §4.3).
 *
 * Elle était écrite deux fois, à l'identique, dans `discover/` et `swipe/` —
 * la situation même que `GOAL-014-T06` avait corrigée pour la bandelette. Le
 * créneau est le seul point commun visuel des deux modes ; le réunir évite d'y
 * corriger deux fois ce qui doit l'être une.
 *
 * **Décorative, sans description** (SPECS.md §7.1) : le flux ne fournit aucun
 * texte alternatif, et une description forgée sur place ajouterait un nœud à
 * parcourir sans rien apprendre.
 *
 * **Un échec de chargement referme le créneau** plutôt que d'y laisser un cadre
 * teinté : une image qu'on ne peut pas obtenir ne se distingue en rien, pour le
 * lecteur, d'un article qui n'en a pas.
 *
 * @param testTag repère propre à l'écran appelant : les deux modes ont le leur,
 *   et les absorber ici les confondrait dans les tests d'écran.
 */
@Composable
fun ArticleIllustration(
    imageUrl: String?,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()

    if (imageUrl == null || state is AsyncImagePainter.State.Error) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
            // Peinte sous l'image, cette teinte n'est visible que tant qu'il n'y
            // a rien à montrer : elle dit que la place est réservée, sans
            // prétendre être une illustration.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ILLUSTRATION_PLACEHOLDER_ALPHA))
            .testTag(testTag),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
