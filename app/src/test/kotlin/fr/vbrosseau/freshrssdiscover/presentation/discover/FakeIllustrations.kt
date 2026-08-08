package fr.vbrosseau.freshrssdiscover.presentation.discover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
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

/**
 * URL dont l'image est **plus petite que n'importe quel créneau**.
 *
 * C'est le cas que le fond flouté corrige (SPECS.md §4.3) : étirée pour remplir
 * seize neuvièmes de la largeur d'un téléphone, une image de cette taille sort
 * floue. Elle existe ici pour que ce cas soit capturable sans réseau.
 */
const val TINY_IMAGE_URL = "https://exemple.org/minuscule.jpg"

/** Couleur de l'image factice : franche, pour se repérer sur une capture. */
private val FakeIllustrationColor = Color.rgb(0x2E, 0x5A, 0x8C)

/** Couleurs de l'image minuscule : deux, et c'est indispensable. */
private val TinyIllustrationColor = Color.rgb(0xC0, 0x5A, 0x2E)
private val TinySubjectColor = Color.rgb(0xF2, 0xE8, 0xD5)

/**
 * Côté de l'image factice, volontairement **carrée**.
 *
 * Un créneau dont la hauteur suivrait l'image reçue mesurerait donc un carré :
 * c'est ce qui rend vérifiable le fait qu'elle n'en dépend pas.
 *
 * **Assez large pour n'être jamais agrandie** : c'est ce qui en fait le cas
 * ordinaire. À 400 px elle passait sous la largeur d'un créneau de téléphone,
 * donc sous le fond flouté de SPECS.md §4.3 — toutes les captures du dépôt
 * auraient illustré le cas particulier en croyant montrer le cas général.
 */
private const val FAKE_ILLUSTRATION_SIDE = 1600

/** Côté de l'image minuscule : bien en deçà de la largeur d'un créneau réel. */
private const val TINY_ILLUSTRATION_SIDE = 120

/**
 * Une image minuscule **à deux tons**, et non un aplat.
 *
 * Un aplat ne prouverait rien : flouté ou net, rogné ou ajusté, il rend
 * exactement les mêmes pixels. La première capture du fond flouté a d'ailleurs
 * été produite ainsi, et ne montrait aucune différence — elle aurait validé
 * n'importe quoi. Un disque clair sur fond sombre rend au contraire visibles
 * les trois choses qui comptent : le sujet reste net, le fond est estompé, et
 * le créneau est plein jusqu'aux bords.
 */
private fun tinyTwoToneImage(): coil3.Image {
    val bitmap = createBitmap(TINY_ILLUSTRATION_SIDE, TINY_ILLUSTRATION_SIDE)
    Canvas(bitmap).apply {
        drawColor(TinyIllustrationColor)
        val middle = TINY_ILLUSTRATION_SIDE / 2f
        drawCircle(middle, middle, middle / 2f, Paint().apply { color = TinySubjectColor })
    }
    return bitmap.asImage()
}

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
    val tiny = tinyTwoToneImage()
    val engine = FakeImageLoaderEngine.Builder()
        .intercept({ it.toString() == LOADABLE_IMAGE_URL }, illustration)
        .intercept({ it.toString() == TINY_IMAGE_URL }, tiny)
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
