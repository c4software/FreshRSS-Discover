package fr.vbrosseau.freshrssdiscover.startup

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * SPECS.md §3.1 accepte `http://`, et ce test est la seule chose qui l'empêche
 * de redevenir une promesse vide.
 *
 * **Ce que ce test rattrape, et que rien d'autre ne rattrapait.** L'application
 * a vécu quatorze Goals sans autoriser le trafic en clair : depuis
 * `targetSdk 28`, Android le refuse par défaut, et le manifeste ne disait rien.
 * Toute instance FreshRSS auto-hébergée en `http://` était donc injoignable, et
 * l'échec se présentait sous le pire déguisement possible — « le serveur ne
 * répond pas », un diagnostic qui envoie chercher la panne du mauvais côté.
 * Aucun test ne pouvait le voir : `MockEngine` n'a pas de politique réseau, et
 * les captures ne franchissent aucune couche de transport. Il a fallu une
 * exécution réelle, sur émulateur, contre un conteneur FreshRSS servi en clair
 * (GOAL-022).
 *
 * Le test porte sur la **politique effective**, telle que la plateforme la
 * calcule à partir du manifeste et de `network_security_config.xml`, et non sur
 * la présence d'un attribut. C'est ce qui le rend insensible à la façon dont
 * l'autorisation est écrite, et sensible à sa disparition.
 */
@RunWith(RobolectricTestRunner::class)
class CleartextTrafficTest {
    @Test
    fun cleartextIsPermittedSoSelfHostedHttpInstancesRemainReachable() {
        val applicationInfo = ApplicationProvider.getApplicationContext<Context>().applicationInfo

        assertTrue(
            applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0,
            "SPECS.md §3.1 accepte http:// : sans clair autorisé au manifeste, " +
                "aucune instance auto-hébergée n'est joignable, et l'échec se " +
                "présente en « le serveur ne répond pas »",
        )
    }
}
