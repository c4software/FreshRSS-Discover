package fr.vbrosseau.freshrssdiscover.reminder

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram

/** Counts sessions and serves a settable histogram, nothing else. */
class FakeReadingSessionRecorder(
    var histogram: ReadingHistogram = ReadingHistogram.Empty,
) : ReadingSessionRecorder {

    var recordedSessions: Int = 0
        private set

    override suspend fun recordSession() {
        recordedSessions++
    }

    override suspend fun histogram(): ReadingHistogram = histogram
}
