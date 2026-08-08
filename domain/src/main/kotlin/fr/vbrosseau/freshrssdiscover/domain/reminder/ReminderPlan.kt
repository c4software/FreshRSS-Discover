package fr.vbrosseau.freshrssdiscover.domain.reminder

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/** Nombre de titres cités dans un rappel. */
const val REMINDER_TITLE_COUNT: Int = 2

/**
 * Les formulations possibles d'un rappel.
 *
 * Un type énuméré et non des chaînes : le domaine choisit **laquelle**, la
 * couche Android sait la dire. Toute chaîne affichée est une ressource
 * (AGENTS.md §9), et le domaine n'en connaît aucune — il ignore d'ailleurs la
 * langue de l'utilisateur.
 *
 * Plusieurs formulations parce qu'un rappel quotidien identique cesse d'être lu
 * au bout de trois jours : l'œil apprend la forme du message et le balaie sans
 * le voir. Varier n'est pas une coquetterie, c'est ce qui le maintient lisible.
 */
enum class ReminderTone {
    /** « Des articles vous attendent. » */
    Waiting,

    /** « Un moment pour lire ? » */
    Invitation,

    /** « Voici ce qui est arrivé depuis hier. » */
    Fresh,

    /** « Votre pile n'attend que vous. » */
    Pile,
}

/**
 * Ce qu'un rappel doit dire, ou l'absence de rappel.
 *
 * `null` — rendu par [reminderPlanFor] — signifie **ne pas notifier du tout**.
 * Un rappel qui annoncerait qu'il n'y a rien à lire est une interruption sans
 * contrepartie, et c'est exactement ce qui fait désactiver les notifications
 * d'une application.
 *
 * @property titles les titres cités, dans l'ordre du flux. Ils viennent des
 *   articles eux-mêmes : c'est du **contenu**, pas un libellé d'interface, et
 *   rien n'est donc à traduire.
 */
data class ReminderPlan(
    val tone: ReminderTone,
    val unreadCount: Int,
    val titles: List<String>,
)

/**
 * Décide du rappel du jour à partir de ce que le cache contient.
 *
 * **Le ton tourne avec le jour**, et de façon déterministe : deux exécutions du
 * même jour — une reprise après échec, un redémarrage de l'appareil — donnent
 * le même message, alors qu'un tirage au hasard en donnerait deux différents
 * pour un seul rappel. Deux jours de suite ne partagent jamais leur ton.
 *
 * @param dayIndex un numéro de jour strictement croissant, typiquement le
 *   nombre de jours depuis l'époque. Seul son reste importe.
 */
fun reminderPlanFor(
    unread: List<Article>,
    dayIndex: Long,
): ReminderPlan? {
    if (unread.isEmpty()) return null

    val tones = ReminderTone.entries
    // `Math.floorMod` et non `%` : un `dayIndex` négatif — une date antérieure à
    // 1970, qu'une horloge d'appareil mal réglée peut produire — rendrait un
    // index négatif, et l'accès à la liste échouerait.
    val tone = tones[Math.floorMod(dayIndex, tones.size.toLong()).toInt()]

    return ReminderPlan(
        tone = tone,
        unreadCount = unread.size,
        titles = unread.take(REMINDER_TITLE_COUNT).map(Article::title),
    )
}
