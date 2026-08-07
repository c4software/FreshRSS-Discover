package fr.vbrosseau.freshrssdiscover.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * Écran de connexion.
 *
 * Sans état : il affiche [uiState] et remonte les gestes. C'est ce qui le rend
 * prévisualisable et testable sans graphe d'injection (AGENTS.md §9).
 */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onServerAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onApiPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Sans cela, le clavier recouvre le champ du mot de passe, qui est
            // le dernier de la liste.
            .imePadding()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.login_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ServerAddressField(
            value = uiState.serverAddress,
            onValueChange = onServerAddressChange,
            isFaulty = uiState.failure?.concernsAddressField == true,
            enabled = !uiState.isSubmitting,
        )

        if (uiState.showsInsecureWarning) {
            InsecureConnectionWarning()
        }

        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.login_username_label)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LoginTestTags.USERNAME_FIELD),
        )

        ApiPasswordField(
            value = uiState.apiPassword,
            onValueChange = onApiPasswordChange,
            enabled = !uiState.isSubmitting,
        )

        uiState.failure?.let { failure -> FailureMessage(failure) }

        SubmitButton(uiState = uiState, onSubmit = onSubmit)

        if (uiState.isSubmitting) {
            ConnectingIndicator()
        }
    }
}

@Composable
private fun ServerAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    isFaulty: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_server_label)) },
        placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
        supportingText = { Text(stringResource(R.string.login_server_help)) },
        isError = isFaulty,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.SERVER_FIELD),
    )
}

@Composable
private fun ApiPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Purement local à l'affichage : le ViewModel n'a pas à savoir si le mot de
    // passe est masqué, et le faire transiter par l'état le ferait survivre à
    // l'écran.
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_password_label)) },
        // L'explication est affichée d'emblée, pas après un échec : c'est la
        // première cause de refus, et son existence n'est pas évidente.
        supportingText = { Text(stringResource(R.string.login_password_help)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier.testTag(LoginTestTags.PASSWORD_VISIBILITY),
            ) {
                Icon(
                    painter = painterResource(
                        if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    // Un mot de passe API se recopie depuis un gestionnaire :
                    // pouvoir vérifier ce qui a été collé évite un échec dont
                    // la cause resterait invisible.
                    contentDescription = stringResource(
                        if (visible) R.string.login_password_hide else R.string.login_password_show,
                    ),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.PASSWORD_FIELD),
    )
}

@Composable
private fun InsecureConnectionWarning(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.login_insecure_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(LoginTestTags.INSECURE_WARNING),
    )
}

/**
 * L'échec est une carte, pas une simple ligne de texte.
 *
 * Certains messages font trois lignes — celui de l'API désactivée nomme le
 * chemin exact dans l'administration. Noyé dans le formulaire, il ne serait pas
 * lu, et c'est pourtant lui qui contient le geste à faire.
 */
@Composable
private fun FailureMessage(failure: LoginFailure, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.FAILURE),
    ) {
        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

@Composable
private fun SubmitButton(uiState: LoginUiState, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onSubmit,
        enabled = uiState.canSubmit,
        modifier = modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.SUBMIT),
    ) {
        Text(stringResource(if (uiState.isSubmitting) R.string.login_submitting else R.string.login_submit))
    }
}

/**
 * Progression **hors** du bouton, et non dedans.
 *
 * Le bouton est désactivé pendant l'appel — c'est ce qui empêche un double
 * envoi — et Material atténue tout son contenu, indicateur compris. Placé à
 * l'intérieur, il devenait quasi invisible : la capture de l'état « connexion
 * en cours » ne montrait qu'un point gris sur fond gris, et l'utilisateur
 * n'avait aucun signe que quelque chose se passait. Constaté sur
 * `connexion-en-cours-clair.png`.
 */
@Composable
private fun ConnectingIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.PROGRESS),
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    AppTheme(dynamicColor = false) {
        LoginScreen(
            uiState = LoginUiState(
                serverAddress = "rss.exemple.org",
                username = "alice",
                apiPassword = "secret",
                canSubmit = true,
            ),
            onServerAddressChange = {},
            onUsernameChange = {},
            onApiPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenFailurePreview() {
    AppTheme(dynamicColor = false) {
        LoginScreen(
            uiState = LoginUiState(
                serverAddress = "http://rss.exemple.org",
                username = "alice",
                showsInsecureWarning = true,
                failure = LoginFailure.Server(AuthError.ApiDisabled),
            ),
            onServerAddressChange = {},
            onUsernameChange = {},
            onApiPasswordChange = {},
            onSubmit = {},
        )
    }
}
