package fr.vbrosseau.freshrssdiscover.data.api

/**
 * Issue brute d'un appel à l'API, **avant** toute interprétation métier.
 *
 * La couche API ne décide pas de ce qu'un `401` signifie pour l'utilisateur :
 * elle rapporte ce que le serveur a répondu. La traduction en `AuthError` a
 * lieu au-dessus, dans un composant que l'on peut éprouver séparément — et qui
 * doit distinguer des cas que le seul code HTTP ne suffit pas à trancher
 * (docs/freshrss-api.md §5).
 */
internal sealed interface ApiOutcome<out T> {
    data class Success<T>(val value: T) : ApiOutcome<T>

    /** Le serveur a répondu, avec un statut hors `2xx`. [body] est du texte brut. */
    data class HttpError(val status: Int, val body: String) : ApiOutcome<Nothing>

    /**
     * Le serveur a répondu `2xx`, mais son corps n'a pas la forme attendue.
     *
     * Cas réel : un portail captif, une page de maintenance ou un proxy qui
     * répond `200` à tout. Le confondre avec une erreur HTTP ferait afficher un
     * diagnostic faux.
     */
    data class MalformedResponse(val detail: String) : ApiOutcome<Nothing>

    /** La requête n'a pas abouti : DNS, TLS, délai dépassé, absence de réseau. */
    data class TransportError(val cause: Throwable) : ApiOutcome<Nothing>
}
