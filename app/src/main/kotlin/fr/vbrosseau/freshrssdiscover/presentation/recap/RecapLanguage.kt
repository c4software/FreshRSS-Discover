package fr.vbrosseau.freshrssdiscover.presentation.recap

/**
 * The language the digest must come out in, spelled out in English
 * (e.g. "French"), the form the prompt instructs the model with.
 *
 * A port rather than a direct `Locale.getDefault()` in the ViewModel: the
 * device locale is ambient state, and tests must be able to pin it. Bound in
 * `RecapModule` to the device language — whatever it is, with no allow-list
 * (SPECS.md §4.10).
 */
fun interface RecapLanguage {
    fun displayName(): String
}
