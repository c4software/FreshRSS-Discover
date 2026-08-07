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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

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
 * Une seule action est réellement branchée, la déconnexion. Les seuils de
 * lecture et le cache sont affichés en lecture seule — voir les `TODO` posés
 * plus bas et dans [SettingsViewModel].
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSignOutRequest: () -> Unit,
    onSignOutConfirm: () -> Unit,
    onSignOutDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        AccountSection(account = uiState.account)
        HorizontalDivider()
        ReadingSection(uiState = uiState)
        HorizontalDivider()
        CacheSection()
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
 * Seuils du marquage automatique (SPECS.md §4.5), en lecture seule.
 *
 * TODO(GOAL-011) : les rendre modifiables et les enregistrer. Aucun stockage de
 *  réglages n'existe encore ; tant qu'il manque, ces valeurs sont celles
 *  compilées dans `ReadDetector` et un contrôle de saisie ne serait qu'un
 *  leurre.
 */
@Composable
private fun ReadingSection(uiState: SettingsUiState, modifier: Modifier = Modifier) {
    SettingsSection(title = stringResource(R.string.settings_section_reading), modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_reading_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsRow(
            label = stringResource(R.string.settings_visible_fraction_label),
            value = stringResource(R.string.settings_visible_fraction_value, uiState.visibleFractionPercent),
            testTag = SettingsTestTags.VISIBLE_FRACTION,
        )
        SettingsRow(
            label = stringResource(R.string.settings_continuous_visibility_label),
            value = stringResource(
                R.string.settings_continuous_visibility_value,
                uiState.continuousVisibilitySeconds,
            ),
            testTag = SettingsTestTags.CONTINUOUS_VISIBILITY,
        )
        Text(
            text = stringResource(R.string.settings_reading_not_editable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Taille du cache et purge manuelle (SPECS.md §6), en lecture seule.
 *
 * TODO(GOAL-011) : mesurer la taille du cache et brancher la purge manuelle.
 *  Le bouton est volontairement désactivé plutôt qu'absent : SPECS.md §6 annonce
 *  l'action, et la masquer laisserait croire qu'elle n'est pas prévue.
 */
@Composable
private fun CacheSection(modifier: Modifier = Modifier) {
    SettingsSection(title = stringResource(R.string.settings_section_cache), modifier = modifier) {
        SettingsRow(
            label = stringResource(R.string.settings_cache_size_label),
            value = stringResource(R.string.settings_cache_size_unavailable),
            testTag = SettingsTestTags.CACHE_SIZE,
        )
        Text(
            text = stringResource(R.string.settings_cache_not_available),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .testTag(SettingsTestTags.PURGE_CACHE),
        ) {
            Text(stringResource(R.string.settings_purge_cache))
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
                visibleFractionPercent = 60,
                continuousVisibilitySeconds = 1,
                appVersion = "0.1.0",
            ),
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
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
                visibleFractionPercent = 60,
                continuousVisibilitySeconds = 1,
                appVersion = "0.1.0",
                isSignOutConfirmationVisible = true,
            ),
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
        )
    }
}
