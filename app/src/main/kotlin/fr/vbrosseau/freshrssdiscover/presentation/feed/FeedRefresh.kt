package fr.vbrosseau.freshrssdiscover.presentation.feed

/**
 * Le rechargement, tel qu'une destination le publie à la barre de titre.
 *
 * Ce type existe parce que le bouton et le flux ne vivent pas au même endroit :
 * la barre de titre appartient à l'ossature de l'application, au-dessus du
 * graphe de navigation, tandis que l'action appartient au ViewModel de la
 * destination affichée. L'un des deux doit franchir la frontière, et c'est
 * l'action — remonter la barre dans chaque écran obligerait chacun à
 * redessiner un titre et une barre de navigation.
 *
 * `null` en l'absence de cette valeur signifie « cette destination n'a rien à
 * recharger », et non « rien à faire pour le moment » : c'est ce qui laisse la
 * barre nue sur l'écran de réglages, sans qu'il ait à le demander.
 */
data class FeedRefresh(
    val isRefreshing: Boolean,
    val onRefresh: () -> Unit,
)
