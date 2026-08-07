package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Accès à la session de l'utilisateur.
 *
 * Déclaré ici, implémenté dans `:app` : c'est ce qui permet au domaine
 * d'exprimer ce dont il a besoin sans rien connaître de HTTP ni du disque
 * (ARCHITECTURE.md §2).
 */
interface AuthRepository {
    /**
     * Session courante, `null` si l'utilisateur n'est pas connecté.
     *
     * Un flux et non une lecture ponctuelle : c'est lui qui ramène
     * l'utilisateur à l'écran de connexion lorsque le serveur refuse le jeton,
     * sans que l'écran ait à interroger quoi que ce soit (SPECS.md §3.4).
     */
    fun observeSession(): Flow<AuthSession?>

    /**
     * Ouvre une session et la conserve.
     *
     * Le mot de passe API ne quitte pas cet appel : il n'est jamais enregistré.
     */
    suspend fun signIn(
        address: ServerAddress,
        credentials: Credentials,
    ): AuthResult<AuthSession>

    /**
     * Dernière adresse et dernier identifiant employés, même sans session.
     *
     * Ils survivent à un jeton refusé : SPECS.md §3.4 demande de ramener
     * l'utilisateur à l'écran de connexion **sans lui faire tout retaper**,
     * alors qu'il n'a probablement qu'un mot de passe API à renouveler.
     */
    fun observeLastSignInHint(): Flow<SignInHint?>

    /**
     * Le serveur a refusé le jeton : la session tombe, le rappel de saisie
     * demeure.
     *
     * Distinct de [signOut], qui est un geste délibéré de l'utilisateur et
     * n'a aucune raison de laisser une trace.
     */
    suspend fun invalidateSession()

    /** Efface la session **et** le rappel de saisie. Geste délibéré de l'utilisateur. */
    suspend fun signOut()
}
