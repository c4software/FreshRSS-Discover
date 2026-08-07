package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.flow.Flow

/**
 * Propage au serveur les articles que [ReadDetector] a déclarés lus.
 *
 * Déclaré ici, implémenté dans `:app` : le domaine exprime ce dont il a besoin
 * sans rien connaître de HTTP ni du disque (ARCHITECTURE.md §2).
 *
 * **Le marquage est optimiste** (SPECS.md §4.5), et c'est ce qui dicte tout le
 * reste de cette interface. L'état local bascule immédiatement, la transmission
 * suit — donc la pose de l'intention ([markAsRead]) et son envoi ([flush]) sont
 * deux opérations distinctes. Les fondre en une seule rendrait le marquage
 * dépendant du réseau : l'article resterait non lu tant que la requête n'aurait
 * pas abouti, et un tunnel de métro suffirait à faire réapparaître dans le flux
 * ce que l'utilisateur vient de lire.
 *
 * **Ce qui n'est pas transmis n'est jamais perdu.** La file survit au
 * redémarrage et n'est purgée qu'après confirmation du serveur, article par
 * article. Une seule chose la vide sans confirmation : [clearPending], à la
 * déconnexion.
 */
interface ReadSyncRepository {
    /**
     * Marque des articles comme lus **localement**, et met la transmission en
     * file.
     *
     * Ne rend aucune issue, et c'est délibéré : du point de vue de l'appelant,
     * cette opération ne peut pas échouer. Elle n'attend pas le réseau — elle
     * ne le touche même pas. Un `ReadSyncOutcome` ici obligerait l'écran à
     * traiter un échec qu'il n'a pas à montrer (SPECS.md §4.5).
     *
     * Idempotent : le flux repasse sur les mêmes articles au gré du
     * défilement, remarquer un article déjà lu ne produit rien.
     */
    suspend fun markAsRead(ids: Set<ArticleId>)

    /**
     * Transmet ce qui attend, par lots, et n'acquitte qu'après confirmation.
     *
     * Appelée après un marquage et au démarrage — c'est ce second appel qui
     * réalise le rejeu promis par SPECS.md §4.5 pour ce qui n'a pas pu partir
     * avant la fermeture de l'application.
     *
     * Sans rien en file, ne touche pas au réseau et rend
     * [ReadSyncOutcome.Synchronized] : il serait absurde de payer un
     * aller-retour pour n'envoyer aucun article.
     */
    suspend fun flush(): ReadSyncOutcome

    /**
     * Nombre de marquages restant à transmettre.
     *
     * Un flux et non une lecture ponctuelle : l'indicateur retombe de lui-même
     * quand la file se vide, sans que l'affichage ait à interroger quoi que ce
     * soit.
     */
    fun observePendingCount(): Flow<Int>

    /**
     * Abandonne ce qui attend, sans le transmettre.
     *
     * Réservé à la déconnexion (SPECS.md §3.5) : ces marquages appartiennent au
     * compte que l'on vient de quitter, et les envoyer sous une autre session
     * marquerait comme lus les articles de quelqu'un d'autre. C'est la **seule**
     * façon de vider la file sans confirmation du serveur — en particulier, une
     * session expirée ne la vide pas.
     */
    suspend fun clearPending()
}
