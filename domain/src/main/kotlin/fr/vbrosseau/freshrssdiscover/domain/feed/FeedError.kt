package fr.vbrosseau.freshrssdiscover.domain.feed

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome

/**
 * Causes d'échec de la récupération d'articles.
 *
 * Volontairement plus court que `AuthError` : les causes de l'authentification
 * — API désactivée, hôte qui n'est pas FreshRSS, identifiants refusés — ne
 * peuvent plus survenir une fois la session ouverte. Les reprendre obligerait
 * chaque appelant à traiter des cas impossibles.
 */
sealed interface FeedError {
    /** Aucune connectivité : la requête n'a pas quitté l'appareil. */
    data object NoNetwork : FeedError

    /** Le serveur ne répond pas : DNS, délai dépassé, TLS refusé. */
    data object ServerUnreachable : FeedError

    /**
     * Le serveur a refusé le jeton.
     *
     * Arrive sans préavis lorsque l'utilisateur change son mot de passe API.
     * Ce n'est pas une erreur de lecture mais une fin de session : le dépôt
     * l'accompagne d'une invalidation, et l'aiguillage racine ramène de
     * lui-même à l'écran de connexion (SPECS.md §3.4).
     */
    data object SessionExpired : FeedError

    /**
     * Défaillance qu'aucun des cas ci-dessus ne décrit.
     *
     * [technicalMessage] va aux journaux, **jamais à l'affichage**.
     */
    data class Unexpected(val technicalMessage: String) : FeedError
}

/** Issue d'une lecture du flux. */
typealias FeedResult<T> = Outcome<T, FeedError>
