package fr.vbrosseau.freshrssdiscover.data.recap

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapAvailability
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapDownloadEvent
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecapGenerator] over ML Kit's GenAI Prompt API — Gemini Nano served by
 * AICore, entirely on the device.
 *
 * The client is created lazily and kept for the app's lifetime: `getClient`
 * is cheap but not free, and the model behind it is a system service — there
 * is nothing to release per call.
 */
@Singleton
internal class MlKitRecapGenerator @Inject constructor() : RecapGenerator {

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /**
     * `runCatching`, not just the status switch: on a device without AICore
     * the call itself can throw, and "the platform errored" and "the platform
     * said no" both mean the same thing to the feature — no button.
     */
    override suspend fun availability(): RecapAvailability =
        runCatching { model.checkStatus().toRecapAvailability() }
            .getOrDefault(RecapAvailability.Unavailable)

    override fun download(): Flow<RecapDownloadEvent> =
        model.download().mapNotNull { status -> status.toRecapDownloadEvent() }

    override fun generate(prompt: String): Flow<String> =
        model.generateContentStream(prompt).mapNotNull { response -> response.chunkText() }
}

/**
 * Unknown values collapse to [RecapAvailability.Unavailable]: a status this
 * version does not know is a status it cannot honour, and hiding the button
 * is the only harmless answer.
 */
internal fun Int.toRecapAvailability(): RecapAvailability =
    when (this) {
        FeatureStatus.AVAILABLE -> RecapAvailability.Available
        FeatureStatus.DOWNLOADABLE -> RecapAvailability.Downloadable
        FeatureStatus.DOWNLOADING -> RecapAvailability.Downloading
        else -> RecapAvailability.Unavailable
    }

/**
 * `DownloadStarted` maps to nothing: the domain flow opens with the first
 * progress event, and a zero-byte [RecapDownloadEvent.Progress] would say
 * the same thing twice.
 */
internal fun DownloadStatus.toRecapDownloadEvent(): RecapDownloadEvent? =
    when (this) {
        is DownloadStatus.DownloadStarted -> null
        is DownloadStatus.DownloadProgress -> RecapDownloadEvent.Progress(totalBytesDownloaded)
        is DownloadStatus.DownloadCompleted -> RecapDownloadEvent.Completed
        is DownloadStatus.DownloadFailed -> RecapDownloadEvent.Failed
    }

/**
 * Empty chunks are dropped rather than emitted: the model closes its stream
 * with them, and the UI appending "" would still recompose for nothing.
 */
internal fun GenerateContentResponse.chunkText(): String? =
    candidates.firstOrNull()?.text?.takeIf { it.isNotEmpty() }
