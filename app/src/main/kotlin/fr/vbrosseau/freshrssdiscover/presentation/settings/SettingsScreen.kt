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

/**
 * Minimum touch target height (SPECS.md §7.1).
 *
 * Material 3 draws its buttons at 40 dp: without this floor, no button on the
 * screen would reach the required 48 dp.
 */
private val MinTouchTarget = 48.dp

/**
 * Disabled content opacity as Material 3 defines it.
 *
 * Copied because it is not published: `SliderDefaults` applies it to its
 * track, but nothing exposes the value to the accompanying texts. Diverging
 * would make the label look brighter than the slider it describes.
 */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** Dims a color when the control it styles is disabled. */
private fun Color.dimmedUnless(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = DISABLED_CONTENT_ALPHA)

/**
 * Settings screen (SPECS.md §6).
 *
 * Stateless: it renders [uiState] and forwards gestures, keeping it
 * previewable and testable without an injection graph (AGENTS.md §9).
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
     * All required, no `{}` defaults: `AppNavHost` wires all nine callbacks,
     * and a silent default would leave a visible but inert control with
     * nothing flagging it.
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
         * No screen title here: the `Scaffold` bar already shows the
         * destination label, and adding one produced two stacked titles on
         * device. Screenshots could not show it: they render the screen
         * alone, without its scaffold.
         */
        AccountSection(account = uiState.account)
        HorizontalDivider()
        /*
         * Before automatic marking and right after the account: this is the
         * only setting that changes what the user sees on opening the app,
         * so the one they come for. Placing it lower would put it behind two
         * fine-tuning sliders and the local cache section.
         *
         * The order also carries meaning: the §4.5 thresholds read
         * differently per mode. In Swipe, a full-screen article immediately
         * satisfies the surface threshold and only duration decides, so
         * knowing the mode before adjusting thresholds is the natural
         * reading order.
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
         * After automatic marking and before the cache: the previous sections
         * concern what happens during reading, the following ones the device
         * and the account. The reminder belongs to the first group.
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
 * Choice between the two ways of browsing the feed (SPECS.md §4.8, §6).
 *
 * Segments rather than a toggle: the setting has exactly two exclusive,
 * equally legitimate values; neither is the "enabled" option of the other. A
 * toggle would name only one ("Swipe mode"), leaving the off position's
 * meaning implicit. A dropdown would hide the alternative behind a tap, and
 * radio buttons would say the same thing in three times the height on an
 * already long screen. Segments show both options and the active one at a
 * glance, and a single tap changes it.
 *
 * The sentence below: "List" and "Swipe" name the gesture, not the outcome.
 * The description follows the selected segment and says what the feed will
 * show, which the control alone cannot convey.
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

/** One test tag per mode: without it, segments differ only by their text. */
private fun presentationTestTagOf(presentation: FeedPresentation): String = when (presentation) {
    FeedPresentation.List -> SettingsTestTags.PRESENTATION_LIST
    FeedPresentation.Swipe -> SettingsTestTags.PRESENTATION_SWIPE
}

/**
 * Automatic marking (SPECS.md §4.5): a switch, then its thresholds.
 *
 * A toggle rather than segments, unlike the presentation mode: the setting
 * does not have two equally legitimate values (marking happens or it does
 * not), and the off position names itself.
 *
 * The two sliders stay visible, dimmed: hiding them would remove two settings
 * without saying why and change the screen height under the finger; leaving
 * them active would offer to adjust something no longer applied.
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
        // The whole row carries the action, for the reason given on the
        // reading reminder: a `Switch` alone is a 32 dp tall target.
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
            value = stringResource(uiState.visibleFraction.label.resId, uiState.visibleFraction.label.argument),
            threshold = uiState.visibleFraction,
            onValueChange = onVisibleFractionChange,
            valueTestTag = SettingsTestTags.VISIBLE_FRACTION,
            sliderTestTag = SettingsTestTags.VISIBLE_FRACTION_SLIDER,
            enabled = uiState.isAutoMarkAsReadEnabled,
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_continuous_visibility_label),
            value = stringResource(
                uiState.continuousVisibility.label.resId,
                uiState.continuousVisibility.label.argument,
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
 * An adjustable threshold: its current value spelled out, then a stepped slider.
 *
 * A slider rather than a list of values because the user's gesture is
 * comparative ("mark earlier" or "later"), not the choice of a number: the
 * thumb position also shows where in the range the value lies, which radio
 * buttons do not. The steps avoid illusory precision and make every value
 * reachable (SPECS.md §7.1).
 *
 * The value stays written above: SPECS.md §6 requires displaying thresholds,
 * and a slider alone does not say what it is worth.
 *
 * @param enabled false when automatic marking is off (SPECS.md §4.5). Label
 *   and value dim with the slider: a value left dark above a grey track would
 *   read as a setting still applied.
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
            // `Slider` only works in `Float`, and a continuous one emits every
            // intermediate value: the threshold snaps the position back onto
            // an increment the repository accepts.
            onValueChange = { onValueChange(threshold.snapped(it)) },
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
 * Daily reading reminder, which can be turned off (SPECS.md §4.9, §6).
 *
 * A toggle where the presentation mode uses segments: this setting does not
 * have two equally legitimate values (there is a reminder or there is not),
 * and the off position names itself.
 *
 * The whole row is touchable, not just the switch. A Material 3 `Switch`
 * measures 52 x 32 dp: hitting its track demands a precision SPECS.md §7.1
 * refuses to require. The row carries the `toggleable`, the label responds
 * like the switch, and the screen reader announces one element instead of
 * two.
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
            // `null`: the row already carries the action, and a second handler
            // would make the switch a distinct element for the screen reader.
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
 * Cache size and manual purge (SPECS.md §6).
 *
 * The size is an article count, not bytes: it is the only figure answering
 * the button's question (what is lost) and the only one that visibly changes
 * on press (see `CacheStatus`).
 *
 * No confirmation: the purge only removes read content already transmitted to
 * the server. The button disables when there is nothing to delete rather than
 * allowing a no-op press.
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
 * Sign-out button, tinted with the error color.
 *
 * The action is destructive (SPECS.md §3.5): the color distinguishes it from
 * other commands before the confirmation is even shown.
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
 * Confirmation required by SPECS.md §3.5.
 *
 * The message lists what disappears (token, username, cache) because the user
 * cannot guess it: "sign out" does not suggest that already downloaded
 * content goes with it.
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
 * A label/value pair, stacked vertically.
 *
 * Two lines rather than a right-hand column: a server address is long, and a
 * two-column layout would truncate it as soon as the system font size is
 * increased (SPECS.md §7.1).
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
