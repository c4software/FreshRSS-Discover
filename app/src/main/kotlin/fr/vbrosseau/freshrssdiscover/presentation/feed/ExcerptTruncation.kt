package fr.vbrosseau.freshrssdiscover.presentation.feed

/** Marque de troncature, ajoutée seulement lorsque du texte a été retiré. */
private const val ELLIPSIS = "…"

/**
 * Écourte un texte sans couper un mot en deux.
 *
 * La coupure se fait sur la dernière espace avant la limite : une phrase
 * tranchée au milieu d'un mot se lit comme un défaut d'affichage, pas comme un
 * extrait. Un texte sans aucune espace — jeton, URL — est coupé net, faute de
 * mieux.
 *
 * Écrite une fois pour les deux modes : seule la borne change entre la carte
 * de la Liste et le plein écran du Balayage (SPECS.md §8, questions 7 et 8) —
 * deux copies de l'algorithme divergeraient au premier correctif.
 */
internal fun String.truncatedAtWord(maxLength: Int): String {
    if (length <= maxLength) return this

    val cut = take(maxLength)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > 0) cut.take(lastSpace) + ELLIPSIS else cut + ELLIPSIS
}
