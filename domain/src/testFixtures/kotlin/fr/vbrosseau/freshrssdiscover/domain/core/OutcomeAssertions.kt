package fr.vbrosseau.freshrssdiscover.domain.core

/**
 * Assertion shortcuts on [Outcome], for tests only.
 *
 * Production code consumes an [Outcome] through an exhaustive `when`; that is
 * the point of the type. These extensions live here rather than next to the
 * type because exposing them to production code would invite ignoring one of
 * the two cases.
 */
fun <T> Outcome<T, *>.valueOrNull(): T? =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> null
    }

/** The error if the operation failed, `null` otherwise. */
fun <E> Outcome<*, E>.errorOrNull(): E? =
    when (this) {
        is Outcome.Success -> null
        is Outcome.Failure -> error
    }
