package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * Indicator shown until a screen receives its first observed state.
 *
 * Rendering a `UiState`'s default values would show an "empty" screen (list
 * with no entries, factory settings) while Room, DataStore, or the system
 * deliver their first value. That emptiness would read as data, not as
 * waiting.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(LoadingTestTags.INDICATOR))
    }
}
