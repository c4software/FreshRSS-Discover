package fr.vbrosseau.freshrssdiscover.presentation.feed

import android.os.Build
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
 * Rayon du flou du fond.
 *
 * Assez large pour que le sujet n'y soit plus lisible — un fond où l'on
 * distingue encore la scène entre en concurrence avec l'image nette posée
 * dessus — et pas au point d'aplatir la teinte, qui est justement ce qui relie
 * le fond à l'image.
 */
private val BLUR_RADIUS = 24.dp

/**
 * Débordement de la copie floutée, au-delà du créneau.
 *
 * `blur` estompe jusqu'aux bords : sans ce léger agrandissement, le fond
 * laisserait voir la teinte du créneau sur son pourtour, et le cadre qu'on
 * cherche à supprimer reparaîtrait en périphérie.
 */
private const val BLUR_OVERSCAN = 1.1f

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

    var slotWidthPx by remember { mutableIntStateOf(0) }
    val sourceWidthPx = (state as? AsyncImagePainter.State.Success)?.result?.image?.width ?: 0
    val blurred = supportsBlur && needsUpscaling(sourceWidthPx = sourceWidthPx, slotWidthPx = slotWidthPx)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
            // Peinte sous l'image, cette teinte n'est visible que tant qu'il n'y
            // a rien à montrer : elle dit que la place est réservée, sans
            // prétendre être une illustration.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ILLUSTRATION_PLACEHOLDER_ALPHA))
            .onSizeChanged { slotWidthPx = it.width }
            .testTag(testTag),
    ) {
        if (blurred) {
            /*
             * La **même** image, rognée pour remplir et floutée : le créneau
             * reste plein, sans bande vide ni cadre, et le fond s'accorde
             * toujours au sujet puisqu'il en vient. C'est l'astuce employée par
             * plusieurs réseaux sociaux, et elle est ici préférable à une
             * couleur dominante — qui demanderait de lire les pixels, donc un
             * calcul par image.
             *
             * Le flou porte sur une copie **agrandie** au-delà du créneau
             * (`scale`) : sans cela, les bords du flou laisseraient voir le
             * fond à travers l'estompage de `blur`, et le cadre reparaîtrait
             * par où on l'a chassé.
             */
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(BLUR_OVERSCAN)
                    .blur(BLUR_RADIUS),
                contentScale = ContentScale.Crop,
            )
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            /*
             * `Inside` et non `Fit` : `Fit` remplit la plus petite dimension du
             * créneau, donc **agrandit encore** — l'image de devant restait
             * floue, constaté sur appareil. `Inside` réduit ce qui déborde mais
             * ne grandit jamais au-delà de la taille native : c'est la seule
             * échelle qui garantit une image nette, puisque c'est la seule qui
             * n'invente aucun pixel.
             */
            contentScale = if (blurred) ContentScale.Inside else ContentScale.Crop,
        )
    }
}

/**
 * `Modifier.blur` n'a d'effet qu'à partir d'Android 12 (API 31) ; le projet
 * descend à 26.
 *
 * En dessous, **rien ne change** : l'image reste étirée comme aujourd'hui.
 * C'est une dégradation franche, préférée à un second mécanisme — le fond
 * serait sinon net et dupliqué, c'est-à-dire pire que le défaut corrigé.
 */
private val supportsBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
