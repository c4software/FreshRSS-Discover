package fr.vbrosseau.freshrssdiscover.domain.settings

/**
 * Les deux seuils du marquage automatique (SPECS.md §4.5), dans les unités du
 * domaine : une fraction de hauteur affichée et une durée en millisecondes.
 *
 * **Pourquoi ce type existe.** Ces deux valeurs vivaient jusqu'ici en copies
 * indépendantes : deux constantes privées de `ReadDetector`, deux constantes
 * du `SettingsViewModel`, des littéraux dans les tests. Rien n'empêchait les
 * copies de diverger, et la divergence aurait été **silencieuse** : l'écran de
 * réglages aurait continué d'annoncer « 60 % pendant 1 s » pendant que le
 * détecteur en appliquait d'autres. Un réglage qui affiche autre chose que ce
 * qui est appliqué est pire qu'un réglage absent — c'est la raison d'être de
 * ce type. [Default] est désormais la déclaration unique côté réglages, et
 * `ReadingSettingsTest` constate qu'elle produit le même comportement que les
 * défauts compilés dans `ReadDetector`.
 *
 * Les bornes sont vérifiées à la construction plutôt que laissées à l'appelant.
 * La source d'une valeur hors bornes n'est pas l'interface — un curseur à crans
 * ne peut pas en produire — mais le **disque** : un fichier de préférences
 * écrit par une version antérieure, tronqué, ou modifié. Voir [coerced], qui
 * est le chemin prévu pour ce cas.
 */
data class ReadingSettings(
    val visibleFraction: Float,
    val continuousVisibilityMillis: Long,
) {
    init {
        require(visibleFraction in VisibleFractionRange) {
            "fraction visible hors bornes : $visibleFraction"
        }
        require(continuousVisibilityMillis in ContinuousVisibilityRange) {
            "durée de visibilité continue hors bornes : $continuousVisibilityMillis"
        }
    }

    companion object {
        /**
         * Fraction de hauteur exigée, entre 20 % et 100 % **inclus**.
         *
         * Le plafond est 1.0 et non davantage : SPECS.md §4.5 précise que
         * l'appelant borne la fraction à la part visible de l'écran, donc à 1.0.
         * Un seuil de 2.0 ne serait jamais atteint et **aucun** article ne
         * deviendrait jamais lu, sans que rien ne le signale.
         *
         * Le plancher est 0.2 et non 0.0 : à zéro, la condition de surface est
         * toujours vraie — une fraction négative rendrait de surcroît lu tout
         * article seulement présent dans l'observation. Le seuil de surface
         * existe précisément pour écarter l'article effleuré en bord d'écran
         * (SPECS.md §4.5) ; en dessous de 20 %, il ne filtre plus rien et le
         * double seuil se réduit à un seuil simple.
         */
        val VisibleFractionRange: ClosedFloatingPointRange<Float> = 0.2f..1.0f

        /**
         * Durée d'affichage continu exigée, entre 1 s et 5 s **incluses**.
         *
         * Le plancher est la valeur de SPECS.md §4.5 : c'est déjà la durée la
         * plus courte qui distingue une lecture d'un défilement rapide. Zéro ou
         * une durée négative satisferaient la condition dès la première
         * observation, ce qui annulerait le second seuil — l'article traversé
         * par un défilement rapide redeviendrait lu, exactement le cas que le
         * double seuil écarte.
         *
         * Le plafond est 5 s parce qu'au-delà, dans un défilement normal, plus
         * aucun article n'atteindrait le seuil : le réglage serait alors
         * indiscernable d'une panne du marquage.
         */
        val ContinuousVisibilityRange: LongRange = 1_000L..5_000L

        /**
         * Les valeurs de SPECS.md §4.5, appliquées tant que rien n'est enregistré.
         *
         * Elles doivent rester identiques aux défauts de `ReadDetector` : c'est
         * la seule chose qui garantit qu'une première installation applique bien
         * ce que l'écran de réglages affiche.
         */
        val Default: ReadingSettings =
            ReadingSettings(
                visibleFraction = 0.6f,
                continuousVisibilityMillis = 1_000L,
            )

        /**
         * Ramène des valeurs quelconques dans les bornes, sans échouer.
         *
         * Réservé à la relecture du disque : une préférence corrompue ne doit
         * pas empêcher l'application de démarrer, alors qu'un appel de
         * l'interface hors bornes est un défaut de programmation et doit lever.
         *
         * `NaN` est ramené au défaut plutôt qu'à une borne : il ne se compare à
         * rien, donc `coerceIn` le laisserait passer tel quel, et un seuil `NaN`
         * rendrait toute comparaison fausse — aucun article ne serait plus
         * jamais marqué lu.
         */
        fun coerced(
            visibleFraction: Float,
            continuousVisibilityMillis: Long,
        ): ReadingSettings =
            ReadingSettings(
                visibleFraction =
                    if (visibleFraction.isNaN()) {
                        Default.visibleFraction
                    } else {
                        visibleFraction.coerceIn(VisibleFractionRange)
                    },
                continuousVisibilityMillis =
                    continuousVisibilityMillis.coerceIn(
                        ContinuousVisibilityRange.first,
                        ContinuousVisibilityRange.last,
                    ),
            )
    }
}
