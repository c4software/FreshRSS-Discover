package fr.vbrosseau.freshrssdiscover.data.security

/**
 * Chiffre et déchiffre les secrets avant leur écriture sur disque.
 *
 * L'abstraction existe pour une raison précise : l'implémentation réelle
 * s'adosse à `AndroidKeyStore`, que Robolectric ne sait pas simuler. Sans elle,
 * le stockage de session ne serait éprouvable dans aucun test — c'est-à-dire
 * qu'on ne vérifierait ni la persistance, ni l'effacement à la déconnexion, qui
 * sont pourtant la partie où les fautes se logent.
 */
internal interface SecretCipher {
    /** Chiffre [plainText] et renvoie une forme transportable en texte. */
    fun encrypt(plainText: String): String

    /**
     * Déchiffre [cipherText], ou renvoie `null` si c'est impossible.
     *
     * Le `null` n'est pas théorique : la clé du *keystore* est perdue lorsque
     * l'utilisateur change son verrouillage d'écran ou restaure une sauvegarde
     * sur un autre appareil. Le secret devient alors illisible, et la seule
     * conduite correcte est de traiter la session comme absente — pas de
     * planter.
     */
    fun decrypt(cipherText: String): String?
}
