package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing
import kotlin.math.roundToInt

/**
 * Hauteur minimale des cibles tactiles (SPECS.md §7.1).
 *
 * Material 3 dessine ses boutons sur 40 dp : sans ce plancher, aucun bouton de
 * l'écran n'atteindrait les 48 dp exigés.
 */
private val MinTouchTarget = 48.dp

/**
 * Opacité d'un contenu désactivé, telle que Material 3 la définit.
 *
 * Recopiée parce qu'elle n'est pas publiée : `SliderDefaults` l'applique à sa
 * piste, mais rien n'expose la valeur aux textes qui l'accompagnent. Diverger
 * ferait paraître le libellé plus vif que le curseur qu'il décrit.
 */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** Atténue une couleur quand le contrôle qu'elle habille est désactivé. */
private fun Color.dimmedUnless(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = DISABLED_CONTENT_ALPHA)

/**
 * Écran de réglages (SPECS.md §6).
 *
 * Sans état : il affiche [uiState] et remonte les gestes, ce qui le rend
 * prévisualisable et testable sans graphe d'injection (AGENTS.md §9).
 *
 * Les seuils de lecture sont modifiables et enregistrés, et le cache se purge
 * depuis cet écran.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSignOutRequest: () -> Unit,
    onSignOutConfirm: () -> Unit,
    onSignOutDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibleFractionChange: (Int) -> Unit,
    onContinuousVisibilityChange: (Int) -> Unit,
    /*
     * Tous obligatoires, sans défaut `{}` : `AppNavHost` câble aujourd'hui les
     * neuf rappels, et un défaut silencieux laisserait une commande visible et
     * inerte sans que rien ne le signale — les défauts « à câbler plus tard »
     * qui vivaient ici étaient un fossile du découpage des tâches.
     */
    onPurgeCache: () -> Unit,
    onPresentationChange: (FeedPresentation) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onAutoMarkAsReadChange: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        /*
         * Pas de titre d'écran ici : la barre du `Scaffold` affiche déjà le
         * libellé de la destination. Les deux se sont retrouvés empilés à la
         * première exécution sur appareil — « Paramètres » puis « Réglages »,
         * deux mots pour la même chose. Aucune capture ne pouvait le montrer :
         * elles rendent l'écran seul, sans son ossature.
         */
        AccountSection(account = uiState.account)
        HorizontalDivider()
        /*
         * Avant le marquage automatique, et juste après le compte : c'est le
         * seul réglage qui change ce que l'utilisateur voit en ouvrant
         * l'application, donc celui qu'il vient chercher. Le placer plus bas
         * l'aurait mis derrière deux curseurs de réglage fin — et derrière la
         * section « Cache local », qui parle de l'appareil et non de la lecture.
         *
         * L'ordre porte aussi une explication : les seuils de §4.5 se lisent
         * différemment selon le mode — en Balayage, un article plein écran
         * satisfait d'emblée le seuil de surface et seule la durée décide.
         * Connaître son mode avant de régler ses seuils est donc le bon sens de
         * lecture.
         */
        PresentationSection(
            presentation = uiState.presentation,
            onPresentationChange = onPresentationChange,
        )
        HorizontalDivider()
        ReadingSection(
            uiState = uiState,
            onVisibleFractionChange = onVisibleFractionChange,
            onContinuousVisibilityChange = onContinuousVisibilityChange,
            onAutoMarkAsReadChange = onAutoMarkAsReadChange,
        )
        HorizontalDivider()
        /*
         * Après le marquage automatique et avant le cache : les trois sections
         * précédentes parlent de ce qui se passe pendant la lecture, celles qui
         * suivent de l'appareil et du compte. Le rappel appartient à la
         * première famille — il dit quand l'application redemande à être lue.
         */
        ReminderSection(
            isEnabled = uiState.isReminderEnabled,
            onEnabledChange = onReminderEnabledChange,
        )
        HorizontalDivider()
        CacheSection(cache = uiState.cache, onPurge = onPurgeCache)
        HorizontalDivider()
        AboutSection(appVersion = uiState.appVersion)

        if (uiState.account != null) {
            SignOutButton(onClick = onSignOutRequest)
        }
    }

    if (uiState.isSignOutConfirmationVisible) {
        SignOutConfirmation(onConfirm = onSignOutConfirm, onDismiss = onSignOutDismiss)
    }
}

@Composable
private fun AccountSection(account: SettingsAccount?, modifier: Modifier = Modifier) {
    SettingsSection(title = stringResource(R.string.settings_section_account), modifier = modifier) {
        if (account == null) {
            Text(
                text = stringResource(R.string.settings_no_session),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(SettingsTestTags.NO_SESSION),
            )
        } else {
            SettingsRow(
                label = stringResource(R.string.settings_server_label),
                value = account.serverAddress,
                testTag = SettingsTestTags.SERVER_ADDRESS,
            )
            SettingsRow(
                label = stringResource(R.string.settings_username_label),
                value = account.username,
                testTag = SettingsTestTags.USERNAME,
            )
        }
    }
}

/**
 * Le choix entre les deux façons de parcourir le flux (SPECS.md §4.8, §6).
 *
 * **Pourquoi des segments.** Le réglage a exactement deux valeurs, exclusives,
 * également légitimes — aucune n'est « l'option activée » de l'autre. Une
 * bascule aurait dû en nommer une seule (« Mode balayage »), laissant deviner
 * que la position éteinte s'appelle « Liste » : l'utilisateur ne pourrait pas
 * lire ce qu'il obtiendrait en la basculant. Un menu déroulant cacherait
 * l'alternative derrière un appui, et une liste de boutons radio dirait la même
 * chose en trois fois plus de hauteur, dans un écran déjà long. Les segments
 * montrent les deux possibilités **et** celle qui est active d'un seul regard,
 * et un unique appui suffit à changer.
 *
 * **Pourquoi une phrase dessous.** « Liste » et « Balayage » nomment le geste,
 * pas ce qu'on y gagne. La description suit le segment sélectionné et dit ce
 * que le flux montrera — plusieurs articles à faire défiler, ou un seul en
 * plein écran. C'est ce que le contrôle seul ne peut pas dire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresentationSection(
    presentation: FeedPresentation,
    onPresentationChange: (FeedPresentation) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_presentation), modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_presentation_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val label = stringResource(R.string.settings_presentation_label)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        ) {
            FeedPresentation.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == presentation,
                    onClick = { onPresentationChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = FeedPresentation.entries.size),
                    label = { Text(stringResource(feedPresentationLabelOf(mode))) },
                    modifier = Modifier
                        .heightIn(min = MinTouchTarget)
                        .testTag(presentationTestTagOf(mode)),
                )
            }
        }
        Text(
            text = stringResource(feedPresentationDescriptionOf(presentation)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SettingsTestTags.PRESENTATION_DESCRIPTION),
        )
    }
}

/** Un repère de test par mode : sans lui, les segments ne se distinguent que par leur texte. */
private fun presentationTestTagOf(presentation: FeedPresentation): String = when (presentation) {
    FeedPresentation.List -> SettingsTestTags.PRESENTATION_LIST
    FeedPresentation.Swipe -> SettingsTestTags.PRESENTATION_SWIPE
}

/**
 * Le marquage automatique (SPECS.md §4.5) : un interrupteur, puis ses seuils.
 *
 * **Une bascule et non des segments**, contrairement au mode de parcours : le
 * réglage n'a pas deux valeurs également légitimes — le marquage a lieu, ou il
 * n'a pas lieu — et la position éteinte se nomme d'elle-même.
 *
 * **Les deux curseurs restent affichés, grisés.** Les cacher ferait disparaître
 * deux réglages sans dire pourquoi, et l'écran changerait de hauteur sous le
 * doigt ; les laisser actifs proposerait d'ajuster ce qui ne s'applique plus.
 */
@Composable
private fun ReadingSection(
    uiState: SettingsUiState,
    onVisibleFractionChange: (Int) -> Unit,
    onContinuousVisibilityChange: (Int) -> Unit,
    onAutoMarkAsReadChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_reading), modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_reading_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // La rangée entière porte l'action, pour la raison exposée sur le
        // rappel de lecture : un `Switch` seul est une cible de 32 dp de haut.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(
                    value = uiState.isAutoMarkAsReadEnabled,
                    role = Role.Switch,
                    onValueChange = onAutoMarkAsReadChange,
                )
                .testTag(SettingsTestTags.AUTO_MARK_AS_READ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_auto_mark_as_read_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = uiState.isAutoMarkAsReadEnabled, onCheckedChange = null)
        }
        Text(
            text = stringResource(R.string.settings_auto_mark_as_read_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SettingsTestTags.AUTO_MARK_AS_READ_HELP),
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_visible_fraction_label),
            value = stringResource(R.string.settings_visible_fraction_value, uiState.visibleFraction.value),
            threshold = uiState.visibleFraction,
            onValueChange = onVisibleFractionChange,
            valueTestTag = SettingsTestTags.VISIBLE_FRACTION,
            sliderTestTag = SettingsTestTags.VISIBLE_FRACTION_SLIDER,
            enabled = uiState.isAutoMarkAsReadEnabled,
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_continuous_visibility_label),
            value = stringResource(
                R.string.settings_continuous_visibility_value,
                uiState.continuousVisibility.value,
            ),
            threshold = uiState.continuousVisibility,
            onValueChange = onContinuousVisibilityChange,
            valueTestTag = SettingsTestTags.CONTINUOUS_VISIBILITY,
            sliderTestTag = SettingsTestTags.CONTINUOUS_VISIBILITY_SLIDER,
            enabled = uiState.isAutoMarkAsReadEnabled,
        )
    }
}

/**
 * Un seuil réglable : sa valeur courante en toutes lettres, puis un curseur à crans.
 *
 * Un curseur plutôt qu'une liste de valeurs parce que le geste de l'utilisateur
 * est comparatif — « marquer plus tôt » ou « plus tard » — et non le choix d'un
 * chiffre : la position du pouce montre du même coup où l'on se trouve dans la
 * plage, ce qu'une liste de boutons radio n'indique pas. Les crans, eux,
 * évitent une précision illusoire et rendent chaque valeur atteignable
 * (SPECS.md §7.1).
 *
 * La valeur reste écrite au-dessus : SPECS.md §6 demande d'afficher les seuils,
 * et un curseur seul ne dit pas ce qu'il vaut.
 *
 * @param enabled faux quand le marquage automatique est éteint (SPECS.md §4.5).
 *   Le libellé et la valeur s'atténuent avec le curseur : un chiffre resté noir
 *   au-dessus d'une piste grise se lirait comme un réglage encore appliqué.
 */
@Composable
private fun ThresholdSlider(
    label: String,
    value: String,
    threshold: SettingsThreshold,
    onValueChange: (Int) -> Unit,
    valueTestTag: String,
    sliderTestTag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.dimmedUnless(enabled),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.dimmedUnless(enabled),
            modifier = Modifier.testTag(valueTestTag),
        )
        Slider(
            enabled = enabled,
            value = threshold.value.toFloat(),
            // L'arrondi est imposé par `Slider`, qui ne travaille qu'en `Float` :
            // les crans le font tomber sur une position exacte, mais la
            // conversion doit être explicite pour que le dépôt reçoive bien une
            // des valeurs qu'il accepte.
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = threshold.range.first.toFloat()..threshold.range.last.toFloat(),
            steps = threshold.stepCount,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .semantics { contentDescription = label }
                .testTag(sliderTestTag),
        )
    }
}

/**
 * Le rappel de lecture quotidien, qu'on peut éteindre (SPECS.md §4.9, §6).
 *
 * **Pourquoi une bascule, là où le mode de présentation a des segments.** Ce
 * réglage n'a pas deux valeurs également légitimes : il y a un rappel, ou il
 * n'y en a pas. La position éteinte se nomme d'elle-même, ce qui était
 * précisément ce qui manquait à « Mode balayage ».
 *
 * **La rangée entière est touchable**, et pas seulement l'interrupteur. Un
 * `Switch` de Material 3 mesure 52 × 32 dp : viser sa piste demande une
 * précision que SPECS.md §7.1 refuse d'exiger. La rangée porte donc le
 * `toggleable`, le libellé y répond au même titre que la bascule, et le lecteur
 * d'écran n'annonce qu'un seul élément au lieu de deux.
 */
@Composable
private fun ReminderSection(isEnabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    SettingsSection(title = stringResource(R.string.settings_section_reminder), modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(value = isEnabled, role = Role.Switch, onValueChange = onEnabledChange)
                .testTag(SettingsTestTags.REMINDER),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_reminder_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // `null` : la rangée porte déjà l'action, et un second gestionnaire
            // ferait de la bascule un élément distinct pour le lecteur d'écran.
            Switch(checked = isEnabled, onCheckedChange = null)
        }
        Text(
            text = stringResource(R.string.settings_reminder_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SettingsTestTags.REMINDER_HELP),
        )
    }
}

/**
 * Taille du cache et purge manuelle (SPECS.md §6).
 *
 * La taille est un **nombre d'articles**, pas un poids : c'est le seul chiffre
 * qui réponde à la question que pose le bouton — ce que l'on perd — et le seul
 * qui bouge visiblement quand on appuie dessus (voir `CacheStatus`).
 *
 * Aucune confirmation : la purge n'emporte que du lu déjà transmis au serveur.
 * Le bouton se désactive quand il n'y a rien à supprimer, plutôt que de laisser
 * appuyer sur une action sans effet.
 */
@Composable
private fun CacheSection(cache: SettingsCache, onPurge: () -> Unit, modifier: Modifier = Modifier) {
    SettingsSection(title = stringResource(R.string.settings_section_cache), modifier = modifier) {
        SettingsRow(
            label = stringResource(R.string.settings_cache_size_label),
            value = pluralStringResource(
                R.plurals.settings_cache_article_count,
                cache.articleCount,
                cache.articleCount,
            ),
            testTag = SettingsTestTags.CACHE_SIZE,
        )
        Text(
            text = if (cache.purgeableCount == 0) {
                stringResource(R.string.settings_cache_nothing_to_purge)
            } else {
                pluralStringResource(
                    R.plurals.settings_cache_purgeable,
                    cache.purgeableCount,
                    cache.purgeableCount,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SettingsTestTags.CACHE_PURGEABLE),
        )
        OutlinedButton(
            onClick = onPurge,
            enabled = cache.purgeableCount > 0,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .testTag(SettingsTestTags.PURGE_CACHE),
        ) {
            Text(stringResource(R.string.settings_purge_cache))
        }
        if (cache.lastPurgedCount != null) {
            Text(
                text = pluralStringResource(
                    R.plurals.settings_cache_purged,
                    cache.lastPurgedCount,
                    cache.lastPurgedCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(SettingsTestTags.CACHE_PURGE_RESULT),
            )
        }
    }
}

@Composable
private fun AboutSection(appVersion: String, modifier: Modifier = Modifier) {
    SettingsSection(title = stringResource(R.string.settings_section_about), modifier = modifier) {
        SettingsRow(
            label = stringResource(R.string.settings_version_label),
            value = appVersion,
            testTag = SettingsTestTags.APP_VERSION,
        )
        SettingsRow(
            label = stringResource(R.string.settings_license_label),
            value = stringResource(R.string.settings_license_value),
            testTag = SettingsTestTags.LICENSE,
        )
    }
}

/**
 * Bouton de déconnexion, teinté en couleur d'erreur.
 *
 * L'action est destructrice (SPECS.md §3.5) : la couleur la distingue des
 * autres commandes avant même que la confirmation ne soit posée.
 */
@Composable
private fun SignOutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .testTag(SettingsTestTags.SIGN_OUT),
    ) {
        Text(stringResource(R.string.settings_sign_out))
    }
}

/**
 * La confirmation exigée par SPECS.md §3.5.
 *
 * Le message énumère ce qui disparaît — jeton, identifiant, cache — parce que
 * c'est ce que l'utilisateur ne peut pas deviner : « se déconnecter » ne laisse
 * pas entendre que le contenu déjà téléchargé part avec.
 */
@Composable
private fun SignOutConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sign_out_dialog_title)) },
        text = { Text(stringResource(R.string.settings_sign_out_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag(SettingsTestTags.SIGN_OUT_CONFIRM),
            ) {
                Text(stringResource(R.string.settings_sign_out_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag(SettingsTestTags.SIGN_OUT_CANCEL),
            ) {
                Text(stringResource(R.string.settings_sign_out_cancel))
            }
        },
        modifier = modifier.testTag(SettingsTestTags.SIGN_OUT_DIALOG),
    )
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/**
 * Une ligne libellé / valeur, empilée verticalement.
 *
 * Deux lignes plutôt qu'une colonne à droite : une adresse de serveur est
 * longue, et une mise en page à deux colonnes la tronquerait dès que la taille
 * de police système est augmentée (SPECS.md §7.1).
 */
@Composable
private fun SettingsRow(label: String, value: String, testTag: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AppTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                account = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice"),
                cache = SettingsCache(articleCount = 1_240, purgeableCount = 812),
                appVersion = "0.1.0",
            ),
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
            onVisibleFractionChange = {},
            onContinuousVisibilityChange = {},
            onPurgeCache = {},
            onPresentationChange = {},
            onReminderEnabledChange = {},
            onAutoMarkAsReadChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenSignOutPreview() {
    AppTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                account = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice"),
                cache = SettingsCache(articleCount = 1_240, purgeableCount = 812),
                appVersion = "0.1.0",
                isSignOutConfirmationVisible = true,
            ),
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
            onVisibleFractionChange = {},
            onContinuousVisibilityChange = {},
            onPurgeCache = {},
            onPresentationChange = {},
            onReminderEnabledChange = {},
            onAutoMarkAsReadChange = {},
        )
    }
}
