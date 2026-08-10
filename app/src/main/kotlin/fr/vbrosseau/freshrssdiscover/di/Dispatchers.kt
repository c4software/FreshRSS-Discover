package fr.vbrosseau.freshrssdiscover.di

import javax.inject.Qualifier

/**
 * Qualifiers for injectable `CoroutineDispatcher`s.
 *
 * No component references `Dispatchers.IO` or `Dispatchers.Default` directly:
 * without injection, a test can neither control scheduling nor advance
 * virtual time.
 */

/** Blocking I/O: database, DataStore, system calls. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Computation: rule evaluation, flow transformations. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
