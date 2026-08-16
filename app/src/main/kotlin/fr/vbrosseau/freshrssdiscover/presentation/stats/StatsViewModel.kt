package fr.vbrosseau.freshrssdiscover.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.reminder.ReadingSessionRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Serves the reading histogram to the statistics screen (SPECS.md §6).
 *
 * A one-shot read rather than an observation: the histogram only moves when
 * articles are marked read, which cannot happen while this screen is in
 * front — there is nothing to re-observe during its lifetime.
 *
 * `null` until the disk answered: the screen shows nothing rather than an
 * empty chart that a loaded histogram would immediately replace.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    recorder: ReadingSessionRecorder,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState?>(null)
    val uiState: StateFlow<StatsUiState?> = _uiState

    init {
        viewModelScope.launch {
            _uiState.value = statsUiStateOf(recorder.histogram())
        }
    }
}
