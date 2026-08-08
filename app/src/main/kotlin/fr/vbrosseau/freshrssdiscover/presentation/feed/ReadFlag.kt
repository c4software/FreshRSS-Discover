package fr.vbrosseau.freshrssdiscover.presentation.feed

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
 */
@Composable
fun ReadFlag(modifier: Modifier = Modifier) {
    Surface(
        shape = BookmarkShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .padding(end = Spacing.md)
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
