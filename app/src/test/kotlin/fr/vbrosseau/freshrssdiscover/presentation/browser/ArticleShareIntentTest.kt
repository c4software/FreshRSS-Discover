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
     * Sans sélecteur explicite, Android retient l'application choisie la
     * première fois et les partages suivants partiraient sans rien demander —
     * l'application aurait alors une destination par défaut, ce qu'elle ne doit
     * jamais avoir (SPECS.md §7.4).
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
     * `ArticleSharerTest` recopie ce gabarit pour rester en JVM pure. Le
     * recopier n'est sûr que si quelqu'un constate qu'il est toujours le même :
     * c'est ce que fait cette assertion.
     */
    @Test
    fun theSharedTextFormatIsTheTitleThenTheLink() {
        val format = RuntimeEnvironment.getApplication().getString(R.string.feed_article_share_text)

        assertEquals("%1\$s\n%2\$s", format)
    }
}
