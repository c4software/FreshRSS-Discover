package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.reminder.AndroidReminderNotifier
import fr.vbrosseau.freshrssdiscover.reminder.ReminderNotifier

/**
 * Ce qui montre le rappel de lecture (SPECS.md §4.9).
 *
 * Le travailleur ne dépend que de l'interface : c'est ce qui le rend éprouvable
 * sans `NotificationManager`, et ce qui permet à cette liaison d'être le seul
 * endroit du code qui connaisse l'implémentation Android.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationModule {
    @Binds
    abstract fun bindReminderNotifier(implementation: AndroidReminderNotifier): ReminderNotifier
}
