package fr.vbrosseau.freshrssdiscover.presentation.feed

/**
 * Une illustration doit-elle être **agrandie** pour remplir son créneau ?
 *
 * C'est la définition exacte du défaut que ce module corrige : une image plus
 * étroite que le créneau, étirée pour le remplir, sort floue ou pixelisée
 * (SPECS.md §4.3). Le seuil n'est donc pas un réglage — il se **mesure**, en
 * comparant la largeur de la source à celle qu'on lui demande d'occuper. Un
 * seuil chiffré (« en dessous de 400 px ») aurait dû être défendu, et se
 * serait démenti au premier écran d'une autre densité.
 *
 * Fonction pure, hors de tout `Composable` : elle s'éprouve sans rendu, là où
 * une décision prise au milieu d'un `Box` demanderait une capture pour être
 * vérifiée.
 *
 * **La hauteur ne participe pas.** Le créneau est en 16/9 et le rognage se fait
 * horizontalement dans l'écrasante majorité des cas ; une image assez large est
 * assez définie, quelle que soit sa hauteur. Ajouter la hauteur ferait traiter
 * comme « petites » des bannières parfaitement nettes.
 *
 * @param sourceWidthPx largeur de l'image reçue. Zéro ou négatif — taille
 *   inconnue, image encore en vol — ne déclenche rien : on ne floute pas sur
 *   une supposition.
 * @param slotWidthPx largeur mesurée du créneau, en pixels de l'écran.
 */
internal fun needsUpscaling(sourceWidthPx: Int, slotWidthPx: Int): Boolean =
    sourceWidthPx > 0 && slotWidthPx > 0 && sourceWidthPx < slotWidthPx
