package fr.vbrosseau.freshrssdiscover.presentation.discover

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR

/** Mois moyen, et non calendaire : « il y a 2 mois » n'a pas besoin d'être exact. */
private const val SECONDS_PER_MONTH = 30L * SECONDS_PER_DAY
private const val SECONDS_PER_YEAR = 365L * SECONDS_PER_DAY

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Ancienneté d'un article, sous une forme **non traduite**.
 *
 * SPECS.md §4.3 demande une date relative (« il y a 2 h ») ; AGENTS.md §9
 * interdit de la calculer dans un Composable, et exige que toute chaîne
 * affichée soit une ressource. Les deux se concilient en séparant le calcul —
 * ici, testable en JVM pure — de sa mise en mots, faite à l'affichage.
 */
sealed interface RelativeTime {
    /** Moins d'une minute : afficher « il y a 0 min » serait absurde. */
    data object JustNow : RelativeTime

    data class Minutes(val count: Int) : RelativeTime

    data class Hours(val count: Int) : RelativeTime

    data class Days(val count: Int) : RelativeTime

    data class Months(val count: Int) : RelativeTime

    data class Years(val count: Int) : RelativeTime
}

/**
 * Ancienneté d'une publication à un instant donné.
 *
 * Le temps vient de `Clock` du domaine, jamais de `System.currentTimeMillis()`
 * (AGENTS.md §2) : sans cela, la fonction dépendrait de l'heure de la machine
 * et ne serait pas testable.
 *
 * Une date **future** — horloge du serveur en avance, article postdaté — est
 * ramenée à [RelativeTime.JustNow] plutôt qu'affichée en négatif : c'est faux
 * de quelques minutes, là où « il y a -3 min » serait faux et illisible.
 */
fun relativeTimeSince(
    publishedAtEpochSeconds: Long,
    nowEpochMillis: Long,
): RelativeTime {
    val elapsed = (nowEpochMillis / MILLIS_PER_SECOND - publishedAtEpochSeconds).coerceAtLeast(0L)

    return when {
        elapsed < SECONDS_PER_MINUTE -> RelativeTime.JustNow
        elapsed < SECONDS_PER_HOUR -> RelativeTime.Minutes((elapsed / SECONDS_PER_MINUTE).toInt())
        elapsed < SECONDS_PER_DAY -> RelativeTime.Hours((elapsed / SECONDS_PER_HOUR).toInt())
        elapsed < SECONDS_PER_MONTH -> RelativeTime.Days((elapsed / SECONDS_PER_DAY).toInt())
        elapsed < SECONDS_PER_YEAR -> RelativeTime.Months((elapsed / SECONDS_PER_MONTH).toInt())
        else -> RelativeTime.Years((elapsed / SECONDS_PER_YEAR).toInt())
    }
}
