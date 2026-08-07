package fr.vbrosseau.freshrssdiscover.domain.read

/**
 * Ce qu'une tentative de transmission a produit.
 *
 * Trois issues, et seulement trois, parce que l'appelant n'a que trois
 * conduites possibles :
 *
 * - **[Synchronized]** — plus rien n'attend, il n'y a rien à signaler ;
 * - **[Deferred]** — le réseau n'a pas suivi. Rien n'est perdu, tout repartira
 *   à la prochaine occasion. **Cette issue ne s'affiche pas** : SPECS.md §4.5
 *   demande qu'un échec de transmission ne se voie pas pendant la lecture, et
 *   SPECS.md §5.2 que le mode hors ligne reste discret. Elle existe pour être
 *   journalisée ou déclencher une nouvelle tentative, pas pour alarmer ;
 * - **[SessionLost]** — le serveur refuse le jeton. C'est le seul cas qui
 *   demande quelque chose à l'utilisateur, et il est déjà pris en charge
 *   ailleurs : la session tombe, l'aiguillage racine ramène à l'écran de
 *   connexion (SPECS.md §3.4).
 *
 * Aucune ne rapporte d'échec **par article** : `edit-tag` ne rend aucun compte
 * article par article (docs/freshrss-api.md §4.1), un tel détail serait inventé.
 */
sealed interface ReadSyncOutcome {
    /** Tout ce qui attendait est parti, et le serveur l'a confirmé. */
    data class Synchronized(val transmittedCount: Int) : ReadSyncOutcome

    /**
     * La transmission s'est arrêtée en chemin ; ce qui reste est conservé.
     *
     * [transmittedCount] peut être non nul : une file longue part par lots, et
     * les premiers peuvent aboutir avant que le réseau ne se coupe.
     */
    data class Deferred(val transmittedCount: Int) : ReadSyncOutcome

    /**
     * Le serveur refuse la session, jeton de modification renouvelé compris.
     *
     * La file n'est **pas** vidée pour autant : les marquages doivent survivre
     * à une reconnexion, sans quoi l'utilisateur reverrait comme non lu ce
     * qu'il a lu avant l'expiration.
     */
    data object SessionLost : ReadSyncOutcome
}
