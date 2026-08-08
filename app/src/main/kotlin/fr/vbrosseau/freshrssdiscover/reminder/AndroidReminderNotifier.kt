package fr.vbrosseau.freshrssdiscover.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.freshrssdiscover.MainActivity
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifiant du canal. Il est **gravé** : le changer ferait apparaître un
 * second canal dans les réglages Android et ramènerait à ses valeurs par défaut
 * un rappel que l'utilisateur avait pris la peine de régler — Android ne
 * réapplique jamais les préférences d'un canal à un autre.
 */
private const val CHANNEL_ID = "rappel-de-lecture"

/**
 * Identifiant de la notification, **constant à dessein**.
 *
 * Un rappel du jour remplace donc celui de la veille au lieu de s'empiler à
 * côté. Une pile de rappels quotidiens ne dit rien de plus qu'un seul — le
 * dernier est le seul à jour — et se balaie d'un geste sans être lue. C'est
 * aussi ce qui rend [ReminderNotifier.dismiss] possible sans tenir de registre.
 */
private const val REMINDER_NOTIFICATION_ID = 1

/** Aucune donnée à distinguer d'un rappel à l'autre : un seul code de requête suffit. */
private const val OPEN_APPLICATION_REQUEST_CODE = 0

/**
 * Le rappel de lecture, tel qu'Android le montre (SPECS.md §4.9).
 *
 * `@Singleton` pour que le canal ne soit décrit qu'une fois par processus, et
 * non parce que la classe porte un état : elle n'en a aucun.
 */
@Singleton
class AndroidReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val notifications = NotificationManagerCompat.from(context)

    override fun show(plan: ReminderPlan) {
        // Le refus se constate ici plutôt qu'au moment de programmer le rappel :
        // il peut être retiré depuis les réglages Android à tout instant, et
        // entre la programmation et l'échéance il s'écoule une journée entière.
        if (!notifications.areNotificationsEnabled()) return

        // Créé à chaque envoi, et non une fois pour toutes au démarrage :
        // l'appel est idempotent — Android ignore un canal déjà décrit, sans en
        // écraser les réglages de l'utilisateur — et l'application n'a alors
        // aucune raison de décrire un canal qu'elle n'utilisera peut-être jamais.
        createChannel()

        val body = bodyOf(plan)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(plan.tone.titleRes()))
            .setContentText(body)
            // Le corps cite des titres réels : replié, il est coupé à une ligne
            // et un titre d'article y tient rarement. `BigTextStyle` est ce qui
            // le rend lisible une fois la notification dépliée.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApplication())
            .build()

        notifications.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    override fun dismiss() {
        // Sans effet s'il n'y a rien d'affiché : `cancel` sur un identifiant
        // inconnu ne lève pas. L'appelant n'a donc pas à savoir si un rappel est
        // parti, ni si l'utilisateur l'a déjà écarté.
        notifications.cancel(REMINDER_NOTIFICATION_ID)
    }

    /**
     * Décrit le canal du rappel.
     *
     * Obligatoire depuis Android 8 : une notification postée sans canal n'est
     * pas affichée, et l'échec est silencieux. Le `minSdk` du projet étant 26,
     * il n'y a aucune version à laquelle s'en dispenser.
     *
     * Importance moyenne : le rappel s'annonce dans le volet et la barre
     * d'état, sans surgir par-dessus ce que l'utilisateur est en train de faire.
     * Un rappel de lecture n'est pas une urgence.
     */
    private fun createChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()

        notifications.createNotificationChannel(channel)
    }

    /**
     * L'ouverture de l'application au toucher.
     *
     * `FLAG_IMMUTABLE` : depuis Android 12, un `PendingIntent` doit déclarer
     * s'il est modifiable, et l'omettre lève à la construction. Immuable est le
     * bon choix ici — rien n'a à compléter cette intention, et une intention
     * modifiable confiée au système laisserait une autre application en changer
     * la destination.
     *
     * `FLAG_UPDATE_CURRENT` parce que l'identifiant est constant : sans lui, le
     * rappel du jour rouvrirait l'intention du premier rappel jamais posté.
     */
    private fun openApplication(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            // Le rappel part hors de toute pile d'activités : sans
            // `NEW_TASK`, il n'y aurait aucune tâche où placer l'écran.
            // `CLEAR_TOP` ramène l'application déjà lancée au premier plan au
            // lieu d'en empiler une seconde instance.
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            OPEN_APPLICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Les titres cités, puis ce qu'il reste à lire.
     *
     * Assemblé par une ressource de format et non par une concaténation Kotlin
     * (AGENTS.md §9) : l'ordre des deux parties et leur séparateur appartiennent
     * à la langue.
     */
    private fun bodyOf(plan: ReminderPlan): String {
        val remaining = context.resources.getQuantityString(
            R.plurals.reminder_remaining,
            plan.unreadCount,
            plan.unreadCount,
        )

        val quoted = quotedTitles(plan.titles) ?: return remaining

        return context.getString(R.string.reminder_body, quoted, remaining)
    }

    /**
     * Les titres d'articles, cités tels quels.
     *
     * Ils ne sont ni traduits ni tronqués : c'est du contenu, et `BigTextStyle`
     * les affiche en entier. Le domaine en a déjà retenu deux au plus
     * (`REMINDER_TITLE_COUNT`) ; au-delà, seuls les deux premiers seraient dits,
     * ce que le `else` assume plutôt que d'échouer.
     *
     * `null` lorsqu'il n'y a aucun titre : le corps se réduit alors au compte,
     * ce qui vaut mieux qu'une paire de guillemets vides.
     */
    private fun quotedTitles(titles: List<String>): String? = when (titles.size) {
        0 -> null
        1 -> context.getString(R.string.reminder_titles_one, titles[0])
        else -> context.getString(R.string.reminder_titles_two, titles[0], titles[1])
    }
}

/**
 * La formulation du jour, mise en mots.
 *
 * Le `when` est exhaustif : ajouter un ton sans lui écrire de titre ne
 * compilera pas, ce qui empêche le rappel de retomber en silence sur une
 * formulation générique — c'est-à-dire sur le message quotidien identique que
 * SPECS.md §4.9 cherche précisément à éviter.
 */
@StringRes
private fun ReminderTone.titleRes(): Int = when (this) {
    ReminderTone.Waiting -> R.string.reminder_title_waiting
    ReminderTone.Invitation -> R.string.reminder_title_invitation
    ReminderTone.Fresh -> R.string.reminder_title_fresh
    ReminderTone.Pile -> R.string.reminder_title_pile
}
