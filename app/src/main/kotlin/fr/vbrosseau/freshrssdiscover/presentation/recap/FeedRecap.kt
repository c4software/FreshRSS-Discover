package fr.vbrosseau.freshrssdiscover.presentation.recap

/**
 * The recap action as a destination publishes it to the title bar.
 *
 * Same boundary crossing as `FeedRefresh`, for the same reason: the title bar
 * belongs to the app scaffold while the action belongs to the displayed
 * destination's ViewModel.
 *
 * `null` means "no usable model here" as much as "no feed here": on a device
 * AICore cannot serve, the feature is invisible rather than disabled — a
 * greyed button would promise something the hardware cannot keep
 * (SPECS.md §4.10).
 */
data class FeedRecap(
    val onRecap: () -> Unit,
)
