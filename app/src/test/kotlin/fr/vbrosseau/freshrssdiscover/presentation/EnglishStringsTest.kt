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
 * `values/` est complet, et il est bien l'anglais (GOAL-021-T02).
 *
 * **Ce que ce cas rattrape, et que rien d'autre ne rattrape.** L'interface est
 * bilingue : le français vit dans `values-fr/`, l'anglais dans `values/`, qui
 * est ce que reçoit tout appareil dont la langue n'est pas prévue. Or **tout le
 * reste du dépôt regarde le français** — les cinquante-huit captures Roborazzi
 * sont figées sur `fr-rFR`, et les tests d'écran le sont aussi depuis
 * GOAL-021-T02. Une chaîne oubliée à la traduction retomberait silencieusement
 * sur sa version française, et le défaut ne se verrait que sur l'appareil d'un
 * anglophone.
 *
 * Le contrôle porte donc sur la **complétude**, pas sur la formulation :
 * traduire n'est pas une décision d'interface, et figer chaque phrase anglaise
 * dans une assertion ferait un test qu'il faut réécrire à chaque retouche de
 * style, donc un test qu'on finit par réenregistrer sans le lire.
 *
 * Le repère est qu'une chaîne rendue en `en-rUS` **diffère** de sa version
 * française : c'est ce qui distingue une traduction d'un repli. Les chaînes
 * volontairement identiques dans les deux langues — le nom de l'application,
 * « Discover » — sont écartées nommément, parce qu'un repli et une égalité
 * voulue s'y ressemblent, et qu'aucune règle mécanique ne saurait les séparer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class EnglishStringsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Les chaînes que l'on veut identiques dans les deux langues.
     *
     * `app_name` est un nom propre ; « Discover » est le nom de la destination,
     * emprunté à Google Discover et gardé tel quel en français ; le gabarit
     * `%1$s · %2$s` n'a pas de mots à traduire.
     */
    private val sameInBothLanguages = setOf(
        R.string.app_name,
        R.string.destination_discover,
        R.string.destination_short_discover,
        R.string.discover_article_meta,
        R.string.swipe_article_meta,
        R.string.feed_article_share_text,
        R.string.reminder_body,
    )

    /**
     * Un échantillon large, une chaîne par famille de ressources : `strings`,
     * `strings_discover_feed`, `strings_feed`, `strings_swipe`,
     * `strings_settings`, `strings_reminder`. Un fichier entier oublié dans le
     * déplacement vers `values-fr/` se verrait ici, et c'est le défaut le plus
     * probable de ce Goal.
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
        R.string.swipe_end_of_feed_title,
        R.string.swipe_article_no_link,
        R.string.swipe_offline_banner,
        R.string.settings_section_account,
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
     * Les pluriels aussi : ils vivent dans un autre nœud du fichier de
     * ressources, et un déplacement peut en emporter les uns sans les autres.
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
