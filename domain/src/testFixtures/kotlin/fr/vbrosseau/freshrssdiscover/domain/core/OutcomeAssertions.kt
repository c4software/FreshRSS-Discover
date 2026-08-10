package fr.vbrosseau.freshrssdiscover.domain.core

/**
 * Raccourcis d'assertion sur [Outcome], réservés aux tests.
 *
 * En production, un [Outcome] se consomme par un `when` exhaustif — c'est tout
 * l'argument du type. Ces extensions vivent donc ici, et non à côté du type :
 * offertes au code de production, elles seraient une invitation à ignorer un
 * des deux cas.
 */
fun <T> Outcome<T, *>.valueOrNull(): T? =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> null
    }

/** Erreur si l'opération a échoué, `null` sinon. */
fun <E> Outcome<*, E>.errorOrNull(): E? =
    when (this) {
        is Outcome.Success -> null
        is Outcome.Failure -> error
    }
