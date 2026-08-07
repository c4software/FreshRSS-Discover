package fr.vbrosseau.freshrssdiscover.domain.read

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fenêtre utilisée par ces tests, indépendante de la valeur par défaut du domaine. */
private const val GROUPING_DELAY = 5_000L

/** Nombre d'échantillons de visibilité couverts par une fenêtre, à la cadence de l'écran. */
private const val FAST_MARKS = 5

/** Cadence d'échantillonnage de la visibilité par l'écran. */
private const val FAST_MARK_INTERVAL = 200L

/** De combien un marquage tardif précède l'échéance de la fenêtre. */
private const val LATE_MARK_OFFSET = 1_000L

/**
 * Ce que ces tests éprouvent : **le regroupement retarde la transmission sans
 * jamais l'égarer** (SPECS.md §4.5).
 *
 * Le temps y est entièrement virtuel : la fenêtre se mesure à la milliseconde,
 * et chaque frontière est attaquée des deux côtés — juste avant l'échéance, puis
 * dessus.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadTransmissionSchedulerTest {
    /** Nombre de transmissions **commencées**, la seule chose que compte la fenêtre. */
    private var transmissionCount = 0

    /** Transmissions en cours, et leur maximum : c'est ainsi que le chevauchement se constate. */
    private var runningTransmissions = 0
    private var maxSimultaneousTransmissions = 0

    /** Retient la transmission en cours, pour reproduire un envoi lent. */
    private var suspender: CompletableDeferred<Unit>? = null

    private var outcome: ReadSyncOutcome = ReadSyncOutcome.Synchronized(transmittedCount = 0)

    private suspend fun transmit(): ReadSyncOutcome {
        transmissionCount++
        runningTransmissions++
        maxSimultaneousTransmissions = maxOf(maxSimultaneousTransmissions, runningTransmissions)
        suspender?.await()
        runningTransmissions--
        return outcome
    }

    private fun TestScope.newScheduler() =
        ReadTransmissionScheduler(
            scope = backgroundScope,
            groupingDelayMillis = GROUPING_DELAY,
            transmit = ::transmit,
        )

    /**
     * Amène la fenêtre à échéance.
     *
     * `advanceUntilIdle` ne conviendrait pas : la fenêtre vit dans
     * `backgroundScope`, et l'ordonnanceur de test n'avance pas le temps pour
     * du travail d'arrière-plan — il l'exécute seulement une fois l'échéance
     * atteinte, d'où le `runCurrent` final.
     */
    private fun TestScope.elapseWindow() {
        advanceTimeBy(GROUPING_DELAY)
        runCurrent()
    }

    @Test
    fun schedulingTransmitsNothingBeforeTheGroupingDelay() =
        runTest {
            // La moitié différée de SPECS.md §4.5 : le marquage local est déjà fait
            // quand cette fenêtre s'ouvre, rien ne presse le réseau.
            val scheduler = newScheduler()

            scheduler.schedule()
            advanceTimeBy(GROUPING_DELAY)

            assertEquals(0, transmissionCount)
        }

    @Test
    fun schedulingTransmitsOnceTheGroupingDelayHasElapsed() =
        runTest {
            val scheduler = newScheduler()

            scheduler.schedule()
            advanceTimeBy(GROUPING_DELAY)
            runCurrent()

            assertEquals(1, transmissionCount)
            assertEquals(GROUPING_DELAY, currentTime)
        }

    @Test
    fun severalMarksWithinTheWindowProduceASingleTransmission() =
        runTest {
            // Le cas du défilement : un lot détecté toutes les 200 ms, et une seule
            // requête au bout. C'est exactement ce que le regroupement existe pour
            // faire.
            val scheduler = newScheduler()

            repeat(FAST_MARKS) {
                scheduler.schedule()
                advanceTimeBy(FAST_MARK_INTERVAL)
            }
            elapseWindow()

            assertEquals(1, transmissionCount)
        }

    @Test
    fun aMarkArrivingDuringTheWindowDoesNotPostponeTheTransmission() =
        runTest {
            // L'échéance est fixe, et non glissante : sans cela, un défilement
            // continu repousserait l'envoi indéfiniment, et la seule occasion de
            // transmettre serait l'arrêt du défilement — souvent la fermeture de
            // l'application.
            val scheduler = newScheduler()

            scheduler.schedule()
            advanceTimeBy(GROUPING_DELAY - LATE_MARK_OFFSET)
            scheduler.schedule()
            advanceTimeBy(LATE_MARK_OFFSET)
            runCurrent()

            assertEquals(1, transmissionCount)
            assertEquals(GROUPING_DELAY, currentTime)
        }

    @Test
    fun aMarkArrivingAfterATransmissionOpensANewWindow() =
        runTest {
            val scheduler = newScheduler()

            scheduler.schedule()
            elapseWindow()
            scheduler.schedule()
            elapseWindow()

            assertEquals(2, transmissionCount)
        }

    @Test
    fun transmittingNowDoesNotWaitForTheGroupingDelay() =
        runTest {
            // Le rejeu au démarrage : il n'y a rien à regrouper, seulement à
            // rattraper.
            val scheduler = newScheduler()

            scheduler.transmitNow()

            assertEquals(1, transmissionCount)
            assertEquals(0L, currentTime)
        }

    @Test
    fun transmittingNowReturnsTheOutcome() =
        runTest {
            outcome = ReadSyncOutcome.Deferred(transmittedCount = 3)
            val scheduler = newScheduler()

            assertEquals(ReadSyncOutcome.Deferred(transmittedCount = 3), scheduler.transmitNow())
        }

    @Test
    fun transmittingNowConsumesTheOpenWindow() =
        runTest {
            // Sans cela, l'envoi forcé serait suivi d'un second envoi à vide quand
            // la fenêtre abandonnée arriverait à échéance.
            val scheduler = newScheduler()

            scheduler.schedule()
            scheduler.transmitNow()
            elapseWindow()

            assertEquals(1, transmissionCount)
        }

    @Test
    fun aMarkArrivingDuringATransmissionIsTransmittedByTheNextWindow() =
        runTest {
            // Le cas où un marquage pourrait se perdre : la transmission en cours a
            // peut-être déjà lu la file. Il lui faut donc sa propre fenêtre.
            val scheduler = newScheduler()
            suspender = CompletableDeferred()
            scheduler.schedule()
            elapseWindow()

            scheduler.schedule()
            suspender?.complete(Unit)
            elapseWindow()

            assertEquals(2, transmissionCount)
        }

    @Test
    fun twoTransmissionsNeverOverlap() =
        runTest {
            // Deux envois simultanés liraient la même file avant que l'un des deux
            // ne l'acquitte, et enverraient donc deux fois les mêmes articles.
            val scheduler = newScheduler()
            suspender = CompletableDeferred()
            scheduler.schedule()
            elapseWindow()

            val forced = backgroundScope.async { scheduler.transmitNow() }
            runCurrent()
            assertEquals(1, transmissionCount)

            suspender?.complete(Unit)
            forced.await()

            assertEquals(2, transmissionCount)
            assertEquals(1, maxSimultaneousTransmissions)
        }

    @Test
    fun cancellingDropsTheScheduledTransmission() =
        runTest {
            // La déconnexion : la file est abandonnée, la fenêtre n'a plus rien à
            // dire au serveur.
            val scheduler = newScheduler()

            scheduler.schedule()
            scheduler.cancelScheduled()
            elapseWindow()

            assertEquals(0, transmissionCount)
        }

    @Test
    fun cancellingWithoutAnOpenWindowTransmitsNothing() =
        runTest {
            val scheduler = newScheduler()

            scheduler.cancelScheduled()
            elapseWindow()

            assertEquals(0, transmissionCount)
        }
}
