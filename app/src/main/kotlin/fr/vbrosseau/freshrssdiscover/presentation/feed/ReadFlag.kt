package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Largeur du fanion : assez pour porter une coche, pas plus. */
private val FLAG_WIDTH = 28.dp

/** Hauteur avant l'encoche. Le rapport fait la silhouette de signet. */
private val FLAG_HEIGHT = 34.dp

/** Profondeur de l'encoche, en fraction de la hauteur. */
private const val FLAG_NOTCH_FRACTION = 0.28f

/** Taille de la coche portée par le fanion. */
private val FLAG_ICON_SIZE = 14.dp

/**
 * Opacité du fanion.
 *
 * Il marque un article **déjà lu** : il doit se trouver quand on le cherche, et
 * s'oublier le reste du temps. À pleine opacité il attirait l'œil sur ce qu'il
 * y a de moins intéressant dans le flux — constaté sur appareil.
 *
 * Appliquée à la surface entière, coche comprise : atténuer la seule couleur de
 * fond aurait laissé la coche à pleine intensité, c'est-à-dire l'inverse du
 * résultat cherché.
 */
private const val FLAG_ALPHA = 0.55f

/**
 * La silhouette d'un signet : un rectangle dont le bas rentre en V.
 *
 * Dessinée plutôt que composée d'un `Icon` de signet : le fanion doit être une
 * **surface**, pour porter une couleur pleine qui le détache de n'importe
 * quelle illustration. Une icône seule se perdrait sur une image claire.
 */
private val BookmarkShape = GenericShape { size, _ ->
    val notch = size.height * FLAG_NOTCH_FRACTION
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2f, size.height - notch)
    lineTo(0f, size.height)
    close()
}

/**
 * Marque un article **déjà lu** (SPECS.md §4.5).
 *
 * **Pourquoi cette marque existe.** Les articles lus restent affichés jusqu'au
 * rechargement demandé — c'est ce qui rend le flux stable d'une ouverture à
 * l'autre (SPECS.md §4.1). Leur disparition ne dit donc plus qu'ils ont été
 * lus, et sans signe visible on relit sans le savoir.
 *
 * **Posé en haut de la carte, jamais dans le coin de l'illustration.** Tous les
 * articles n'en ont pas ; un fanion arrimé à l'image aurait demandé un second
 * emplacement, donc un second rendu et un second jeu de captures. Ici la
 * position ne dépend pas du contenu.
 *
 * Le symbole du signet dit d'ordinaire « favori ». La collision est **assumée**
 * par l'auteur, et notée : elle sera à rouvrir le jour où les articles suivis
 * de FreshRSS entreront dans l'application.
 *
 * La description est portée par le fanion lui-même et non par la carte : un
 * lecteur d'écran annonce ainsi l'état sans que le titre ait à le répéter.
 *
 * **Il ne participe pas à la mise en page** : l'appelant le pose en
 * surimpression du haut de carte. Placé dans le flux, il décalait le contenu
 * des articles sans illustration — constaté sur appareil.
 */
@Composable
fun ReadFlag(visible: Boolean, modifier: Modifier = Modifier) {
    /*
     * En fondu, jamais d'un coup. L'état lu s'établit en cours de lecture
     * (SPECS.md §4.5) : un fanion qui surgit sur la carte qu'on est en train de
     * lire attire l'œil sur lui, alors qu'il n'a rien à annoncer — il constate.
     * Le fondu le fait exister sans interrompre.
     *
     * `AnimatedVisibility` plutôt qu'un simple `if` : sans lui, l'apparition est
     * instantanée et la disparition aussi. Ici seule l'apparition survient en
     * pratique, l'état lu ne revenant jamais en arrière.
     */
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        ReadFlagShape()
    }
}

@Composable
private fun ReadFlagShape() {
    Surface(
        shape = BookmarkShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(end = Spacing.md)
            .alpha(FLAG_ALPHA)
            .width(FLAG_WIDTH)
            .height(FLAG_HEIGHT)
            .testTag(FeedTestTags.READ_FLAG),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.feed_article_read),
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .size(FLAG_ICON_SIZE),
            )
        }
    }
}
