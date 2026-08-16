package fr.vbrosseau.freshrssdiscover.domain.recap

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scriptable recap generator for tests.
 *
 * Everything is a plain mutable field: a test states the device it simulates
 * (availability, download outcome, generated chunks) and observes the prompts
 * it received. Generation failure is expressed as the flow throwing, exactly
 * like the real adapter.
 */
class FakeRecapGenerator(
    var availability: RecapAvailability = RecapAvailability.Available,
) : RecapGenerator {
    var downloadEvents: List<RecapDownloadEvent> = listOf(RecapDownloadEvent.Completed)
    var chunks: List<String> = emptyList()
    var generationFailure: Throwable? = null
    val receivedPrompts = mutableListOf<String>()

    override suspend fun availability(): RecapAvailability = availability

    override fun download(): Flow<RecapDownloadEvent> =
        flow {
            downloadEvents.forEach { emit(it) }
        }

    override fun generate(prompt: String): Flow<String> =
        flow {
            receivedPrompts += prompt
            chunks.forEach { emit(it) }
            generationFailure?.let { throw it }
        }
}
