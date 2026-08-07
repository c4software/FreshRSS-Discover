package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
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
    /*
     */
    onVisibleFractionChange: (Int) -> Unit,
    onContinuousVisibilityChange: (Int) -> Unit,
    /**
     * Purge manuelle du cache (SPECS.md §6).
     *
     * Avec une valeur par défaut, contrairement aux autres rappels : la
     * destination Réglages (`AppNavHost`) sort du périmètre de la tâche qui a
     * livré cette section, et un paramètre obligatoire l'aurait empêchée de
     * compiler. **À câbler sur `viewModel::purgeCache`** — voir GOAL-011-T05.
     */
    onPurgeCache: () -> Unit = {},
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
        ReadingSection(
            uiState = uiState,
            onVisibleFractionChange = onVisibleFractionChange,
            onContinuousVisibilityChange = onContinuousVisibilityChange,
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
 * Seuils du marquage automatique (SPECS.md §4.5), modifiables et enregistrés.
 */
@Composable
private fun ReadingSection(
    uiState: SettingsUiState,
    onVisibleFractionChange: (Int) -> Unit,
    onContinuousVisibilityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_reading), modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_reading_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_visible_fraction_label),
            value = stringResource(R.string.settings_visible_fraction_value, uiState.visibleFraction.value),
            threshold = uiState.visibleFraction,
            onValueChange = onVisibleFractionChange,
            valueTestTag = SettingsTestTags.VISIBLE_FRACTION,
            sliderTestTag = SettingsTestTags.VISIBLE_FRACTION_SLIDER,
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
 */
@Composable
private fun ThresholdSlider(
    label: String,
    value: String,
    threshold: SettingsThreshold,
    onValueChange: (Int) -> Unit,
    valueTestTag: String,
    sliderTestTag: String,
    modifier: Modifier = Modifier,
) {
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
            modifier = Modifier.testTag(valueTestTag),
        )
        Slider(
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
        )
    }
}
