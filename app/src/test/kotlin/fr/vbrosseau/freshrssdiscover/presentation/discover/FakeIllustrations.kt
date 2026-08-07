package fr.vbrosseau.freshrssdiscover.presentation.discover

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import kotlinx.coroutines.awaitCancellation
import kotlin.coroutines.EmptyCoroutineContext

/** URL dont le chargement aboutit, dans les tests seulement. */
const val LOADABLE_IMAGE_URL = "https://exemple.org/illustration.jpg"

/** URL dont le chargement échoue, dans les tests seulement. */
const val UNREACHABLE_IMAGE_URL = "https://exemple.org/introuvable.jpg"

/** URL dont le chargement ne se termine jamais : elle fige l'état d'attente. */
const val PENDING_IMAGE_URL = "https://exemple.org/interminable.jpg"

/** Couleur de l'image factice : franche, pour se repérer sur une capture. */
private val FakeIllustrationColor = Color.rgb(0x2E, 0x5A, 0x8C)

/**
 * Côté de l'image factice, volontairement **carrée**.
 *
 * Un créneau dont la hauteur suivrait l'image reçue mesurerait donc un carré :
 * c'est ce qui rend vérifiable le fait qu'elle n'en dépend pas.
 */
private const val FAKE_ILLUSTRATION_SIDE = 400

/**
 * Installe un chargeur d'images déterministe pour la durée d'un test.
 *
 * Aucun test ne doit dépendre du réseau : `FakeImageLoaderEngine` répond à la
 * place du chargeur réel, immédiatement et toujours pareil. Tout ce qui n'est
 * pas [LOADABLE_IMAGE_URL] échoue — c'est ainsi que l'échec de chargement,
 * qui n'est pas un cas exotique sur des flux publics, devient reproductible.
 *
 * `coroutineContext` est vidé de tout répartiteur : la requête s'exécute alors
 * sur le fil appelant. Sans cela le résultat arriverait après la capture, et le
 * test observerait le chargement plutôt que son issue.
 */
@OptIn(DelicateCoilApi::class)
fun installFakeImageLoader() {
    val illustration = ColorImage(
        color = FakeIllustrationColor,
        width = FAKE_ILLUSTRATION_SIDE,
        height = FAKE_ILLUSTRATION_SIDE,
    )
    val engine = FakeImageLoaderEngine.Builder()
        .intercept({ it.toString() == LOADABLE_IMAGE_URL }, illustration)
        .intercept({ it.toString() == PENDING_IMAGE_URL }) { awaitCancellation() }
        .default(
            Interceptor { chain ->
                ErrorResult(
                    image = null,
                    request = chain.request,
                    throwable = IllegalStateException("illustration indisponible"),
                )
            },
        )
        .build()

    val context = ApplicationProvider.getApplicationContext<Context>()
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context)
            .components { add(engine) }
            .coroutineContext(EmptyCoroutineContext)
            .build(),
    )
}

/** Rend le chargeur d'images à son état d'origine, pour ne pas fuiter d'un test à l'autre. */
@OptIn(DelicateCoilApi::class)
fun resetImageLoader() {
    SingletonImageLoader.reset()
}
