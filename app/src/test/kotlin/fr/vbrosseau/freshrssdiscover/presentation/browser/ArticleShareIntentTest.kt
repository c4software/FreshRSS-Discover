package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Intent
import androidx.core.content.IntentCompat
import fr.vbrosseau.freshrssdiscover.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val SHARED_TEXT = "Un titre\nhttps://example.org/article"
private const val CHOOSER_TITLE = "Partager l'article"

@RunWith(RobolectricTestRunner::class)
class ArticleShareIntentTest {
    private val chooser = buildArticleShareIntent(text = SHARED_TEXT, chooserTitle = CHOOSER_TITLE)
    private val send = assertNotNull(
        IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java),
    )

    /**
     * Without an explicit chooser, Android remembers the app picked the first
     * time and subsequent shares would leave without asking; the application
     * would then have a default destination, which it must never have
     * (SPECS.md §7.4).
     */
    @Test
    fun theSystemChooserIsWhatIsLaunched() {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    }

    @Test
    fun theChooserCarriesItsTitleForOlderAndroids() {
        assertEquals(CHOOSER_TITLE, chooser.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun theWrappedIntentSendsPlainText() {
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
    }

    @Test
    fun theWrappedIntentCarriesTheComposedText() {
        assertEquals(SHARED_TEXT, send.getStringExtra(Intent.EXTRA_TEXT))
    }

    /**
     * `ArticleSharerTest` copies this template to stay in pure JVM. Copying it
     * is only safe if something verifies it is still the same: this assertion
     * does.
     */
    @Test
    fun theSharedTextFormatIsTheTitleThenTheLink() {
        val format = RuntimeEnvironment.getApplication().getString(R.string.feed_article_share_text)

        assertEquals("%1\$s\n%2\$s", format)
    }
}
