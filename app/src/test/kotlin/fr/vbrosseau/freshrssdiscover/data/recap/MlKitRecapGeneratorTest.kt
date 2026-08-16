package fr.vbrosseau.freshrssdiscover.data.recap

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapAvailability
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapDownloadEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The AICore inference itself cannot run here — no emulator ships the
 * service. What can go wrong in this adapter is the mapping, so the mapping
 * is what gets pinned: every platform status lands on the domain value the
 * button rule expects, and an unknown one hides the feature instead of
 * crashing it.
 */
class MlKitRecapGeneratorTest {
    @Test
    fun everyKnownFeatureStatusLandsOnItsDomainValue() {
        assertEquals(RecapAvailability.Available, FeatureStatus.AVAILABLE.toRecapAvailability())
        assertEquals(RecapAvailability.Downloadable, FeatureStatus.DOWNLOADABLE.toRecapAvailability())
        assertEquals(RecapAvailability.Downloading, FeatureStatus.DOWNLOADING.toRecapAvailability())
        assertEquals(RecapAvailability.Unavailable, FeatureStatus.UNAVAILABLE.toRecapAvailability())
    }

    @Test
    fun anUnknownFeatureStatusHidesTheFeature() {
        assertEquals(RecapAvailability.Unavailable, Int.MAX_VALUE.toRecapAvailability())
    }

    @Test
    fun downloadProgressCarriesItsBytes() {
        val event = DownloadStatus.DownloadProgress(totalBytesDownloaded = 2_048L).toRecapDownloadEvent()

        assertEquals(RecapDownloadEvent.Progress(totalBytesDownloaded = 2_048L), event)
    }

    @Test
    fun downloadCompletionAndFailureLandOnTheirDomainEvents() {
        assertEquals(RecapDownloadEvent.Completed, DownloadStatus.DownloadCompleted.toRecapDownloadEvent())
        assertEquals(
            RecapDownloadEvent.Failed,
            DownloadStatus.DownloadFailed(GenAiException(RuntimeException("AICore died"), 0))
                .toRecapDownloadEvent(),
        )
    }

    @Test
    fun downloadStartedMapsToNothing() {
        assertNull(DownloadStatus.DownloadStarted(bytesToDownload = 4_096L).toRecapDownloadEvent())
    }
}
