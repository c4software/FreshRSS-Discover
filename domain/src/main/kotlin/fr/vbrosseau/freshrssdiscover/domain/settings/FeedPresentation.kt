package fr.vbrosseau.freshrssdiscover.domain.settings

/**
 * La façon dont le flux se parcourt (SPECS.md §4.8).
 *
 * **Ce n'est pas un réglage d'apparence.** Le contenu est rigoureusement le
 * même dans les deux modes — mêmes articles, même mélange, même ordre, mêmes
 * règles de lecture et de chargement. Seul le geste change, et avec lui le
 * nombre d'articles visibles à la fois. C'est pourquoi le type vit dans le
 * domaine plutôt que dans la couche de présentation : il décide de ce que
 * l'application rouvre après avoir été quittée (SPECS.md §6), donc il se
 * persiste, donc il doit exister là où la persistance est décrite.
 *
 * Un `enum` fermé plutôt qu'un booléen : « balayage activé » obligerait à
 * savoir que le contraire s'appelle « Liste », ce que rien n'indiquerait, et un
 * troisième mode transformerait le type en drapeau contradictoire.
 */
enum class FeedPresentation {
    /** Défilement vertical, plusieurs articles à l'écran en cartes. */
    List,

    /** Balayage horizontal, un article à la fois en plein écran. */
    Swipe,

    ;

    /**
     * La forme écrite sur disque.
     *
     * Le **nom** et non l'`ordinal` : un ordinal lie la valeur enregistrée à
     * l'ordre de déclaration, et intercaler un mode un jour ferait rouvrir
     * l'application dans un autre mode que celui qu'on avait quitté — sans que
     * rien ne le signale, puisque l'entier resterait valide.
     */
    val storedName: String get() = name

    companion object {
        /**
         * **Liste**, imposé par SPECS.md §4.8.
         *
         * C'est le mode qui montre plusieurs articles à la fois : à la première
         * ouverture, il laisse voir de quoi le flux est fait avant de demander
         * un geste. Le balayage se choisit une fois qu'on sait ce qu'on balaye.
         */
        val Default: FeedPresentation = List

        /**
         * Relit une valeur venue du disque, sans jamais échouer.
         *
         * `null` (rien d'enregistré), une chaîne vide, le nom d'un mode retiré
         * depuis, ou un fichier de préférences abîmé retombent tous sur
         * [Default] : un mode de présentation illisible ne doit pas empêcher
         * l'application de démarrer. La valeur corrigée sera réécrite au
         * prochain choix de l'utilisateur.
         */
        fun fromStoredName(raw: String?): FeedPresentation = entries.firstOrNull { it.storedName == raw } ?: Default
    }
}
