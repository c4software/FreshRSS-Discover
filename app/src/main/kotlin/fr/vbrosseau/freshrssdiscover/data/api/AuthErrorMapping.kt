package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError

/**
 * Traduit une issue technique en cause diagnosticable pour l'utilisateur.
 *
 * C'est le seul endroit du projet où un code HTTP prend un sens. Au-dessus, on
 * ne raisonne plus que sur [AuthError] (ARCHITECTURE.md §7).
 *
 * @param isOnline connectivité constatée au moment de l'échec. Elle seule
 *   permet de distinguer « pas de réseau » de « serveur injoignable », que la
 *   pile HTTP rapporte de façon identique — et dont les gestes de correction
 *   n'ont rien à voir : attendre le réseau, ou corriger l'adresse.
 */
internal fun ApiOutcome<*>.toAuthError(isOnline: Boolean): AuthError = when (this) {
    is ApiOutcome.Success -> AuthError.Unexpected("toAuthError appelé sur un succès")

    is ApiOutcome.TransportError ->
        if (isOnline) AuthError.ServerUnreachable else AuthError.NoNetwork

    /*
     * Le serveur a répondu `2xx` mais son corps n'a pas la forme attendue :
     * portail captif, page de maintenance, proxy qui répond 200 à tout. Du
     * point de vue de l'utilisateur, l'adresse ne désigne pas une instance
     * FreshRSS — c'est exactement ce qu'il doit corriger.
     */
    is ApiOutcome.MalformedResponse -> AuthError.NotAFreshRssServer

    is ApiOutcome.HttpError -> httpStatusToAuthError(status, body)
}

private fun httpStatusToAuthError(status: Int, body: String): AuthError = when (status) {
    /*
     * Constaté : identifiant inconnu et mot de passe faux répondent tous deux
     * `401`. Les distinguer permettrait d'énumérer les comptes, et FreshRSS s'y
     * refuse à raison — le message doit donc couvrir les deux hypothèses.
     *
     * Un `401` sur un chemin inexistant tombe aussi ici : l'autorisation est
     * vérifiée avant le routage. C'est sans conséquence, la sonde de
     * reconnaissance ayant déjà écarté ce cas.
     */
    HTTP_UNAUTHORIZED -> AuthError.InvalidCredentials

    /*
     * `400` désigne un identifiant syntaxiquement invalide — vide, espaces,
     * `../`. Ce n'est pas une faute sur le mot de passe, mais du point de vue
     * de l'utilisateur le geste est le même : reprendre ses identifiants.
     */
    HTTP_BAD_REQUEST -> AuthError.InvalidCredentials

    /** Une case à cocher dans l'administration, pas un problème d'identifiants. */
    HTTP_SERVICE_UNAVAILABLE -> AuthError.ApiDisabled

    /*
     * Constaté : un chemin inconnu **sous** l'API répond `401`, jamais `404`.
     * Un `404` désigne donc l'hôte — mauvaise adresse, ou installation dans un
     * sous-répertoire non indiqué.
     */
    HTTP_NOT_FOUND -> AuthError.NotAFreshRssServer

    else -> AuthError.Unexpected("HTTP $status: ${body.take(MAX_TECHNICAL_MESSAGE_LENGTH)}")
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_SERVICE_UNAVAILABLE = 503

/**
 * Le corps d'erreur part dans les journaux : le tronquer évite qu'une page
 * HTML entière — celle d'un portail captif, typiquement — s'y déverse.
 */
private const val MAX_TECHNICAL_MESSAGE_LENGTH = 200
