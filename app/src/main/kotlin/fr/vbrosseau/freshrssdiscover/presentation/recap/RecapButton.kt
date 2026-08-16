package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R

/** Minimum touch target (SPECS.md §7.1), same as the refresh button beside it. */
private val MinTouchTarget = 48.dp

/**
 * Opens the on-device recap of the unread articles (SPECS.md §4.10).
 *
 * On the title row next to the refresh button, and for the same reason: it is
 * a whole-feed command, not an article one. No progress variant here — the
 * work happens in the recap sheet, which carries its own progress; the button
 * only ever opens it.
 */
@Composable
fun RecapButton(
    onRecap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onRecap,
        modifier = modifier
            .size(MinTouchTarget)
            .testTag(RecapTestTags.BUTTON),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_recap),
            contentDescription = stringResource(R.string.recap_button),
        )
    }
}
