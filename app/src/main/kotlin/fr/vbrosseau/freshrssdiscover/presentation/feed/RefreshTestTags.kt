package fr.vbrosseau.freshrssdiscover.presentation.feed

/**
 * Repères de test du bouton de rechargement.
 *
 * Communs aux deux modes de présentation, comme le bouton lui-même : c'est la
 * même commande, et lui donner un repère par mode obligerait chaque test à
 * savoir dans lequel il se trouve.
 */
object RefreshTestTags {
    const val BUTTON = "feed:refresh"
}
