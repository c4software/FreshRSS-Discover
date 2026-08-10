package fr.vbrosseau.freshrssdiscover.domain.core

/**
 * Result of an operation: a value, or a typed error.
 *
 * `kotlin.Result` is not used: it carries a `Throwable`, which would let
 * technical exceptions surface above the `data` layer (ARCHITECTURE.md §7) and
 * leave callers free to handle no case at all. A sealed type is consumed
 * through an exhaustive `when`.
 *
 * The error is a type parameter rather than a single type: each domain has its
 * own causes, and merging them into one enumeration would force handling
 * impossible cases (an article load cannot fail because the API is disabled on
 * the server; authentication would already have reported it).
 *
 * Introduced at the second use case, not the first (AGENTS.md §2).
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>

    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}
