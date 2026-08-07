package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Session ouverte : ce qu'il faut connaître pour parler au serveur.
 *
 * [modificationToken] est nul tant qu'aucune opération modifiante n'a été
 * tentée : il s'obtient par un appel distinct, qu'il serait inutile de payer à
 * chaque connexion (docs/freshrss-api.md §2.3).
 *
 * Le `toString` engendré est sûr : [AuthToken] et [ModificationToken] masquent
 * eux-mêmes leur valeur.
 */
data class AuthSession(
    val server: ServerAddress,
    val username: String,
    val token: AuthToken,
    val modificationToken: ModificationToken? = null,
)
