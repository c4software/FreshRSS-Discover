package fr.vbrosseau.freshrssdiscover.di

import javax.inject.Qualifier

/**
 * Coroutine scope living as long as the process.
 *
 * Reserved for work that must not be cancelled with a screen: notably a sync
 * cycle triggered by a broadcast.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
