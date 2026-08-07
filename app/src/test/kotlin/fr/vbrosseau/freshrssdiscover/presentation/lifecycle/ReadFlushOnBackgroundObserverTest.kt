package fr.vbrosseau.freshrssdiscover.presentation.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Le cycle de vie est piloté par un vrai [LifecycleRegistry] plutôt que par un
 * appel direct à `onStop` : ce qui est vérifié ici, c'est la **correspondance
 * entre l'événement et la transmission**, et appeler la méthode à la main la
 * supposerait acquise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadFlushOnBackgroundObserverTest {

    /**
     * `UnconfinedTestDispatcher` : la transmission part d'un `launch` déclenché
     * hors coroutine, sans rien pour l'attendre. Exécutée sur place, elle est
     * observable dès le retour de l'événement.
     */
    private val applicationScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private val repository = FakeReadSyncRepository()

    @Test
    fun goingToBackgroundTransmitsPendingReads() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.CREATED

        assertEquals(1, repository.flushCallCount)
    }

    /**
     * Le cas qui justifie `ON_STOP` : une boîte de dialogue système passe
     * devant, l'application reste visible et la lecture reprendra dans la
     * seconde. Transmettre ici annulerait le regroupement au lieu de le clore.
     */
    @Test
    fun losingFocusWithoutLeavingDoesNotTransmit() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.STARTED

        assertEquals(0, repository.flushCallCount)
    }

    /**
     * Sans rien en file, l'envoi forcé ne transmet rien : le garde-fou vit dans
     * `flush`, qui ne touche pas au réseau quand la file est vide. L'observateur
     * n'a donc pas à consulter le compteur avant de déclencher — ce serait une
     * lecture de plus pour la même issue.
     */
    @Test
    fun goingToBackgroundWithNothingPendingSendsNothing() {
        repository.nextOutcome = ReadSyncOutcome.Synchronized(transmittedCount = 0)
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.CREATED

        assertEquals(0, repository.pendingCount.value)
        assertEquals(0, repository.markedIds.size)
    }

    /**
     * Revenir puis repartir doit transmettre de nouveau : un observateur qui ne
     * jouerait qu'une fois ne couvrirait que la première session.
     */
    @Test
    fun returningThenLeavingAgainTransmitsOnceMore() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.RESUMED
        owner.registry.currentState = Lifecycle.State.CREATED

        assertEquals(2, repository.flushCallCount)
    }

    /**
     * Rend le propriétaire et non son seul registre : celui-ci ne le retient
     * que faiblement et refuse de fonctionner s'il a été ramassé entre-temps.
     */
    private fun ownerObservedFromResumed(): TestLifecycleOwner = TestLifecycleOwner().apply {
        registry.addObserver(ReadFlushOnBackgroundObserver(repository, applicationScope))
        registry.currentState = Lifecycle.State.RESUMED
    }
}

/**
 * `createUnsafe` : le registre vérifie normalement qu'on le pilote depuis le
 * fil principal, qui n'existe pas dans un test JVM pur. Rien ici n'est
 * concurrent, cette vérification n'a donc rien à protéger.
 */
private class TestLifecycleOwner : LifecycleOwner {
    val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}
