package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId

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
     * Marque des articles comme lus **localement**, met la transmission en file
     * et programme son envoi.
     *
     * Ne rend aucune issue, et c'est délibéré : du point de vue de l'appelant,
     * cette opération ne peut pas échouer. Elle n'attend pas le réseau — elle
     * ne le touche même pas. Une issue ici obligerait l'écran à traiter un
     * échec qu'il n'a pas à montrer (SPECS.md §4.5).
     *
     * **L'appelant n'a rien à envoyer après cet appel.** Le regroupement
     * temporel est du ressort de l'implémentation
     * ([ReadTransmissionScheduler]) : un défilement continu produit un lot
     * toutes les 200 ms, et les faire partir un par un serait la requête par
     * article que le marquage par lots de SPECS.md §4.5 écarte. Enchaîner un
     * [flush] ici annulerait précisément ce regroupement.
     *
     * Idempotent : le flux repasse sur les mêmes articles au gré du
     * défilement, remarquer un article déjà lu ne produit rien.
     */
    suspend fun markAsRead(ids: Set<ArticleId>)

    /**
     * Transmet **sans attendre** ce qui attend, par lots, et n'acquitte
     * qu'après confirmation.
     *
     * C'est la sortie de secours du regroupement : au démarrage — le rejeu
     * promis par SPECS.md §4.5 pour ce qui n'a pas pu partir avant la fermeture
     * de l'application — et partout où attendre n'aurait plus de sens, un
     * passage en arrière-plan par exemple. Le marquage ordinaire, lui, n'a pas
     * à l'appeler.
     *
     * Sans rien en file, ne touche pas au réseau : il serait absurde de payer
     * un aller-retour pour n'envoyer aucun article.
     *
     * Ne rend rien, comme [markAsRead] et pour la même raison : aucun appelant
     * n'a de conduite à tenir selon l'issue. Un échec est un report — la file
     * conserve (SPECS.md §4.5) — et une session refusée est déjà prise en
     * charge par l'implémentation, l'aiguillage racine ramenant de lui-même à
     * l'écran de connexion (SPECS.md §3.4).
     */
    suspend fun flush()

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
