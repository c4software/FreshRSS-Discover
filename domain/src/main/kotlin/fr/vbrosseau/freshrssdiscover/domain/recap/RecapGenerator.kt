package fr.vbrosseau.freshrssdiscover.domain.recap

import kotlinx.coroutines.flow.Flow

/**
 * One step of the model download offered on first use.
 *
 * Progress is in bytes, not in percent: the platform does not announce the
 * total size up front, so a percentage would have to invent its denominator.
 * Failure carries no message — there is nothing actionable to show beyond
 * "it failed, try again", and a platform string would bypass the resource
 * rule anyway.
 */
sealed interface RecapDownloadEvent {
    data class Progress(val totalBytesDownloaded: Long) : RecapDownloadEvent

    data object Completed : RecapDownloadEvent

    data object Failed : RecapDownloadEvent
}

/**
 * On-device generation of the feed recap.
 *
 * Declared here, implemented in `:app` over ML Kit's GenAI Prompt API
 * (ARCHITECTURE.md §2): the domain expresses "a text model that may need
 * installing" without knowing anything about AICore or Gemini Nano. This is
 * also what keeps the privacy promise checkable — the port receives a prompt
 * and returns text, no network type can even appear in the signature.
 *
 * [generate] streams the answer chunk by chunk rather than returning it
 * whole: on-device inference takes seconds, and a digest that builds up
 * on screen is the difference between "working" and "frozen". A failure
 * mid-generation surfaces as the flow throwing; the caller shows what it
 * already received or an error, there is no partial-success state to model.
 */
interface RecapGenerator {
    /**
     * Asked at each display of the feed, not cached: the answer changes when
     * the model finishes downloading, and a stale [RecapAvailability] would
     * hide a button the device just earned.
     */
    suspend fun availability(): RecapAvailability

    /** Installs the model. Completes with the terminal event, never both. */
    fun download(): Flow<RecapDownloadEvent>

    /** Streams the digest for [prompt], in generation order. */
    fun generate(prompt: String): Flow<String>
}
