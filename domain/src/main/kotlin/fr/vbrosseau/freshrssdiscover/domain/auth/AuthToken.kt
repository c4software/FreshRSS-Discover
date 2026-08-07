package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Jeton d'authentification renvoyé par le serveur.
 *
 * Sa forme — `<utilisateur>/<condensat>` — et la façon dont il se transporte
 * dans un en-tête sont des détails de l'API FreshRSS, qui restent confinés à la
 * couche `data` (ARCHITECTURE.md §2.1). Le domaine ne voit qu'une valeur
 * opaque.
 *
 * Il **n'expire pas** : c'est un condensat déterministe du mot de passe API
 * (docs/freshrss-api.md §2.1). Il est donc conservé entre deux lancements. En
 * revanche il devient invalide sans préavis si l'utilisateur change son mot de
 * passe API — d'où [AuthError.InvalidCredentials] sur une requête qui
 * fonctionnait la veille.
 *
 * `toString` masque la valeur : c'est un secret au même titre qu'un mot de
 * passe.
 */
class AuthToken(val value: String) {
    override fun toString(): String = "AuthToken(***)"

    override fun equals(other: Any?): Boolean = this === other || (other is AuthToken && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

/**
 * Jeton exigé par les opérations modifiantes de l'API.
 *
 * Distinct de [AuthToken] : il ne s'obtient qu'une fois authentifié, et se
 * transmet autrement. Les confondre produirait un `401` difficile à
 * diagnostiquer, d'où deux types plutôt qu'un alias de `String`.
 */
class ModificationToken(val value: String) {
    override fun toString(): String = "ModificationToken(***)"

    override fun equals(other: Any?): Boolean = this === other || (other is ModificationToken && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}
