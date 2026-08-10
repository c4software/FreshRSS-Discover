package fr.vbrosseau.freshrssdiscover.domain.time

/**
 * Controlled clock for tests.
 *
 * Only advances on demand: a test verifying chronological order must be able
 * to produce distinct timestamps without actually waiting.
 */
class FakeClock(private var nowMillis: Long = 0L) : Clock {
    override fun nowEpochMillis(): Long = nowMillis

    /** Advances the clock and returns the new value. */
    fun advanceBy(millis: Long): Long {
        nowMillis += millis
        return nowMillis
    }

    fun setTo(millis: Long) {
        nowMillis = millis
    }
}
