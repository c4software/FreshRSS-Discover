package fr.vbrosseau.freshrssdiscover.domain.recap

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The fake will drive the recap screen. These tests pin down what that screen
 * will rely on: the scripted availability is what the button obeys, a download
 * replays its scripted steps in order, and a generation failure surfaces as
 * the flow throwing — after the chunks already emitted, like a model dying
 * mid-answer.
 */
class FakeRecapGeneratorTest {
    private val generator = FakeRecapGenerator()

    @Test
    fun theScriptedAvailabilityIsReturnedAsIs() =
        runTest {
            generator.availability = RecapAvailability.Downloadable

            assertEquals(RecapAvailability.Downloadable, generator.availability())
        }

    @Test
    fun aDownloadReplaysItsScriptedStepsInOrder() =
        runTest {
            generator.downloadEvents =
                listOf(
                    RecapDownloadEvent.Progress(totalBytesDownloaded = 1_024L),
                    RecapDownloadEvent.Completed,
                )

            assertEquals(generator.downloadEvents, generator.download().toList())
        }

    @Test
    fun aFailedDownloadEndsOnTheFailure() =
        runTest {
            generator.downloadEvents = listOf(RecapDownloadEvent.Failed)

            assertEquals(listOf(RecapDownloadEvent.Failed), generator.download().toList())
        }

    @Test
    fun generationStreamsTheChunksAndRecordsThePrompt() =
        runTest {
            generator.chunks = listOf("Un début", ", une fin.")

            val received = generator.generate("le prompt").toList()

            assertEquals(listOf("Un début", ", une fin."), received)
            assertEquals(listOf("le prompt"), generator.receivedPrompts)
        }

    @Test
    fun aGenerationFailureThrowsAfterTheEmittedChunks() =
        runTest {
            generator.chunks = listOf("Un début")
            generator.generationFailure = IllegalStateException("le modèle est mort")

            val received = mutableListOf<String>()
            assertFailsWith<IllegalStateException> {
                generator.generate("le prompt").collect(received::add)
            }
            assertEquals(listOf("Un début"), received)
        }

    @Test
    fun theDeviceStatesAreExactlyTheFourThePlatformReports() {
        // Pins the contract: a fifth state (or a lost one) would silently
        // change what the button-visibility rule means.
        assertEquals(
            listOf(
                RecapAvailability.Unavailable,
                RecapAvailability.Downloadable,
                RecapAvailability.Downloading,
                RecapAvailability.Available,
            ),
            RecapAvailability.entries.toList(),
        )
    }
}
