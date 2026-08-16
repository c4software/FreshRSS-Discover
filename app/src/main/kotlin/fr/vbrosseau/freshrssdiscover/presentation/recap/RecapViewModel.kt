package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapAvailability
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the on-device recap (SPECS.md §4.10).
 *
 * Availability is asked once at construction: the ViewModel lives with the
 * feed destination, so every arrival on the feed asks again, which is the
 * cadence the port documents. The one transition that happens while the
 * screen is up — the model finishing its download — goes through this
 * ViewModel too, so it updates the state itself rather than re-asking.
 */
@HiltViewModel
class RecapViewModel @Inject constructor(
    private val generator: RecapGenerator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecapUiState())
    val uiState: StateFlow<RecapUiState> = _uiState

    init {
        viewModelScope.launch {
            val availability = generator.availability()
            _uiState.update { it.copy(isModelUsable = availability != RecapAvailability.Unavailable) }
        }
    }

    /** The title-bar button. Opening twice is idempotent, not an error. */
    fun onRecapRequested() {
        _uiState.update { it.copy(isSheetOpen = true) }
    }
}
