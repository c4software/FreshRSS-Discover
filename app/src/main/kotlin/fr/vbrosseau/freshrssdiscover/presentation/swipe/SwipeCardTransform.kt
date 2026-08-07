package fr.vbrosseau.freshrssdiscover.presentation.swipe

/**
 * Inclinaison maximale d'une carte qui s'en va, en degrés.
 *
 * Douze, et pas davantage : la carte occupe presque toute la hauteur de
 * l'écran, et le pivot est placé sous elle — un degré de rotation y déplace le
 * coin supérieur de bien plus que sur la vignette carrée d'où vient ce motif.
 * À vingt degrés, le titre sortait du cadre avant que la carte n'ait parcouru
 * la moitié de l'écran.
 */
private const val MAX_ROTATION_DEGREES = 12f

/**
 * Échelle de la carte du dessous quand elle est encore entièrement couverte.
 *
 * Elle doit se voir comme une carte **en attente**, pas comme la même carte
 * rendue floue : 0,92 se distingue nettement au bord sans que le texte ne
 * paraisse rapetissé au moment où elle prend la place de la précédente.
 */
private const val DECK_MIN_SCALE = 0.92f

/**
 * Opacité résiduelle d'une carte parvenue au bord de l'écran.
 *
 * Elle ne descend pas à zéro : une carte qui s'efface complètement avant
 * d'avoir quitté le cadre donne l'impression de se dissoudre sur place, alors
 * que le geste dit qu'on la met de côté.
 */
private const val EXIT_MIN_ALPHA = 0.4f

/**
 * Ce qu'il faut appliquer à une carte pour la faire tenir dans la pile.
 *
 * @property translationXFraction décalage horizontal **à ajouter** à celui que
 *   le pagineur applique déjà, exprimé en fraction de la largeur d'une page.
 * @property drawOrder ordre de dessin : la plus grande valeur passe devant.
 */
data class SwipeCardTransform(
    val translationXFraction: Float,
    val rotationDegrees: Float,
    val scale: Float,
    val alpha: Float,
    val drawOrder: Float,
)

/**
 * La pile de cartes, à partir de la seule position du pagineur.
 *
 * [pageOffset] vaut 0 pour la carte posée, devient **positif** quand elle part
 * vers la gauche, et négatif pour celle qui attend derrière — c'est la
 * convention du pagineur, `(currentPage - page) + currentPageOffsetFraction`.
 *
 * Une seule règle, et elle est symétrique : **la carte au décalage positif est
 * celle qui vole**, l'autre est la pile. En avant, la carte courante s'en va
 * vers la gauche et la suivante monte derrière ; en arrière, c'est la
 * précédente qui revient par la gauche et se repose sur le dessus, pendant que
 * la courante redescend dans la pile. Le même calcul rend les deux sens sans
 * qu'aucun n'ait à être traité à part.
 *
 * La carte qui vole **garde le déplacement du pagineur** : c'est lui qui la
 * sort de l'écran, et le doigt doit la sentir suivre. Celle du dessous
 * l'**annule** au contraire, pour rester centrée — sans quoi elle glisserait
 * depuis le bord comme une page ordinaire, et il n'y aurait pas de pile mais un
 * défilement de plus.
 *
 * Fonction pure, hors de tout `Composable` : c'est ce qui permet d'éprouver la
 * géométrie sans rendre quoi que ce soit, et d'affirmer qu'aucune carte ne
 * devient invisible ou renversée à mi-geste (AGENTS.md §9).
 */
fun swipeCardTransform(pageOffset: Float): SwipeCardTransform {
    if (pageOffset < 0f) {
        // La pile : centrée, à l'échelle, et derrière.
        val revealed = (1f + pageOffset).coerceIn(0f, 1f)
        return SwipeCardTransform(
            translationXFraction = pageOffset,
            rotationDegrees = 0f,
            scale = DECK_MIN_SCALE + (1f - DECK_MIN_SCALE) * revealed,
            alpha = 1f,
            drawOrder = pageOffset,
        )
    }

    val travelled = pageOffset.coerceIn(0f, 1f)
    return SwipeCardTransform(
        translationXFraction = 0f,
        // Négatif : la carte part vers la gauche, donc son sommet penche à
        // gauche — une inclinaison inverse la ferait paraître retenue.
        rotationDegrees = -travelled * MAX_ROTATION_DEGREES,
        scale = 1f,
        alpha = 1f - (1f - EXIT_MIN_ALPHA) * travelled,
        drawOrder = pageOffset,
    )
}
