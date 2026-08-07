package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Causes d'échec d'une authentification, telles que l'utilisateur doit pouvoir
 * les distinguer.
 *
 * Un type unique — « échec de connexion » — rendrait SPECS.md §3.3
 * inapplicable : la spécification exige un message par cause, parce que les
 * gestes de correction sont différents. Vérifier son mot de passe API et
 * activer l'API dans l'administration du serveur n'ont rien à voir.
 *
 * L'énumération est volontairement fermée : la couche `data` doit traduire
 * *toute* défaillance technique vers l'un de ces cas, et le compilateur
 * l'oblige à ne rien laisser passer.
 */
sealed interface AuthError {
    /** Aucune connectivité : la requête n'a pas quitté l'appareil. */
    data object NoNetwork : AuthError

    /** L'adresse ne répond pas : DNS, port fermé, délai dépassé, TLS refusé. */
    data object ServerUnreachable : AuthError

    /**
     * L'adresse répond, mais ce n'est pas une instance FreshRSS.
     *
     * Cas réel et fréquent : l'utilisateur saisit l'adresse de son serveur
     * personnel sans le sous-chemin où FreshRSS est installé.
     */
    data object NotAFreshRssServer : AuthError

    /**
     * L'API est désactivée sur le serveur.
     *
     * FreshRSS répond alors `503` sur **tous** les points d'entrée. C'est une
     * case à cocher dans l'administration, pas un problème d'identifiants — le
     * message doit le dire, sans quoi l'utilisateur vérifiera son mot de passe
     * indéfiniment.
     */
    data object ApiDisabled : AuthError

    /**
     * Identifiant ou mot de passe API refusé.
     *
     * Rappel pour le message affiché : c'est le **mot de passe API** qui est
     * attendu, pas celui de connexion (SPECS.md §3.2).
     */
    data object InvalidCredentials : AuthError

    /**
     * Défaillance qu'aucun des cas ci-dessus ne décrit.
     *
     * [technicalMessage] est destiné aux journaux, **jamais à l'affichage** :
     * il n'est ni traduit ni compréhensible. Il ne doit contenir aucun secret.
     */
    data class Unexpected(val technicalMessage: String) : AuthError
}
