package fr.vbrosseau.freshrssdiscover.startup

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * SPECS.md §3.1 accepts `http://`, and this test is the only thing keeping
 * that from becoming an empty promise.
 *
 * Since `targetSdk 28`, Android refuses cleartext traffic by default; without
 * an explicit allowance, every self-hosted FreshRSS instance on `http://` is
 * unreachable, and the failure shows up as "the server does not respond", a
 * diagnosis pointing the wrong way. No other test can see it: `MockEngine`
 * has no network policy, and screenshots cross no transport layer. It took a
 * real run on an emulator against a cleartext FreshRSS container (GOAL-022).
 *
 * The test checks the effective policy, as the platform computes it from the
 * manifest and `network_security_config.xml`, not the presence of an
 * attribute. That makes it insensitive to how the allowance is written and
 * sensitive to its disappearance.
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
