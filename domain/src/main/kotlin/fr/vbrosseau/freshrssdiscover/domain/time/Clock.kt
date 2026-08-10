package fr.vbrosseau.freshrssdiscover.domain.time

/**
 * Time source of the domain.
 *
 * Only its implementation calls `System.currentTimeMillis()`. Without this
 * abstraction, any timestamped logic would be untestable or dependent on the
 * machine's real time.
 */
fun interface Clock {
    /** Milliseconds elapsed since the Unix epoch. */
    fun nowEpochMillis(): Long
}
