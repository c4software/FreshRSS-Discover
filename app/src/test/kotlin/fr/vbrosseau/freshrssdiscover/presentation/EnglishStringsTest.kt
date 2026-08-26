package fr.vbrosseau.freshrssdiscover.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `values/` is complete, and it is indeed English (GOAL-021-T02).
 *
 * The UI is bilingual: French lives in `values-fr/`, English in `values/`,
 * which is what any device with an unplanned language receives. Everything
 * else in the repository looks at French: the Roborazzi captures are pinned to
 * `fr-rFR`, and so are the screen tests since GOAL-021-T02. A string missed in
 * translation would silently fall back to its French version, and the defect
 * would only show on an English speaker's device.
 *
 * The check is about completeness, not wording: pinning every English
 * sentence in an assertion would make a test that must be rewritten on every
 * style touch-up, hence one that ends up re-recorded without being read.
 *
 * The marker is that a string rendered in `en-rUS` differs from its French
 * version: that is what distinguishes a translation from a fallback. Strings
 * deliberately identical in both languages (the application name, "Discover")
 * are excluded by name, because a fallback and an intended equality look
 * alike there and no mechanical rule can separate them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class EnglishStringsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Strings intended to be identical in both languages.
     *
     * `app_name` is a proper noun; "Discover" is the destination name,
     * borrowed from Google Discover and kept as-is in French; the template
     * `%1$s · %2$s` has no words to translate.
     */
    private val sameInBothLanguages = setOf(
        R.string.app_name,
        R.string.destination_discover,
        R.string.destination_short_discover,
        R.string.discover_article_meta,
        R.string.immersive_article_meta,
        R.string.feed_article_share_text,
        R.string.reminder_body,
    )

    /**
     * A broad sample, one string per resource family: `strings`,
     * `strings_discover_feed`, `strings_feed`, `strings_immersive`,
     * `strings_settings`, `strings_reminder`. A whole file forgotten in the
     * move to `values-fr/` would show here.
     */
    private val translated = listOf(
        R.string.destination_settings,
        R.string.login_title,
        R.string.login_password_help,
        R.string.login_error_api_disabled,
        R.string.discover_end_of_feed,
        R.string.discover_retry,
        R.string.discover_offline_banner,
        R.string.discover_offline_open_blocked,
        R.string.feed_refresh,
        R.string.feed_stale_notice,
        R.string.feed_article_share,
        R.string.immersive_end_of_feed_title,
        R.string.immersive_article_no_link,
        R.string.immersive_offline_banner,
        R.string.settings_section_account,
        R.string.settings_feeds,
        R.string.subscriptions_help,
        R.string.subscriptions_error_rejected,
        R.string.settings_auto_mark_as_read_label,
        R.string.settings_auto_mark_as_read_help,
        R.string.settings_sign_out_dialog_message,
        R.string.reminder_channel_name,
        R.string.reminder_title_invitation,
    )

    @Test
    fun everyTranslatedStringDiffersFromItsFrenchCounterpart() {
        val french = context.inLocale(Locale.FRENCH)

        translated.forEach { id ->
            assertNotEquals(
                french.getString(id),
                context.getString(id),
                "${context.resources.getResourceEntryName(id)} retombe sur le français : " +
                    "la chaîne manque dans values/",
            )
        }
    }

    @Test
    fun stringsMeantToBeIdenticalStayIdentical() {
        val french = context.inLocale(Locale.FRENCH)

        sameInBothLanguages.forEach { id ->
            assertEquals(
                french.getString(id),
                context.getString(id),
                "${context.resources.getResourceEntryName(id)} ne devrait pas être traduite",
            )
        }
    }

    /**
     * Plurals too: they live in a different node of the resource file, and a
     * move can carry some without the others.
     */
    @Test
    fun pluralsAreTranslatedTooAndKeepBothForms() {
        val french = context.inLocale(Locale.FRENCH)

        val singular = context.resources.getQuantityString(R.plurals.reminder_remaining, 1, 1)
        val plural = context.resources.getQuantityString(R.plurals.reminder_remaining, 7, 7)

        assertNotEquals(french.resources.getQuantityString(R.plurals.reminder_remaining, 1, 1), singular)
        assertTrue(singular.contains("article"), "le singulier doit rester lisible : $singular")
        assertTrue(plural.contains("articles"), "le pluriel doit porter sa marque : $plural")
    }

    private fun Context.inLocale(locale: Locale): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })
}
