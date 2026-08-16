package fr.vbrosseau.freshrssdiscover.domain.recap

/**
 * Whether the on-device recap model can serve this device.
 *
 * A pure mirror of what the platform reports, so the presentation layer can
 * decide without touching any ML Kit type: [Unavailable] hides the feature
 * entirely — the device cannot run the model, and a visible-but-disabled
 * button would promise something the hardware cannot keep. [Downloadable] and
 * [Downloading] both show the button: the capability exists, only the weights
 * are missing, and the first tap offers (or follows) the download.
 */
enum class RecapAvailability {
    Unavailable,
    Downloadable,
    Downloading,
    Available,
}
