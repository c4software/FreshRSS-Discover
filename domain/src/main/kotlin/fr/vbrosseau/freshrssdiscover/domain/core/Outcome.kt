package fr.vbrosseau.freshrssdiscover.domain.core

/**
 * Issue d'une opération : une valeur, ou une erreur typée.
 *
 * `kotlin.Result` n'est pas employé : il transporte un `Throwable`, ce qui
 * ferait remonter des exceptions techniques au-dessus de la couche `data`
 * (ARCHITECTURE.md §7) et laisserait l'appelant libre de ne traiter aucun cas.
 * Un type scellé, lui, se consomme par un `when` exhaustif.
 *
 * L'erreur est un paramètre de type, et non un type unique : chaque domaine a
 * ses causes, et les fondre en une seule énumération obligerait à traiter des
 * cas impossibles — un article ne peut pas échouer parce que « l'API est
 * désactivée sur le serveur », l'authentification l'aurait déjà signalé.
 *
 * Créé lors du deuxième cas d'usage, pas du premier : l'authentification s'en
 * passait très bien seule (AGENTS.md §2).
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>

    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}
