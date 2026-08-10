package fr.vbrosseau.freshrssdiscover.presentation.feed

/**
 * Decides whether an illustration would be upscaled to fill its slot.
 *
 * This is the exact defect this module fixes: an image narrower than the slot,
 * stretched to fill it, renders blurry or pixelated (SPECS.md §4.3). The
 * threshold is measured, not configured, by comparing the source width with
 * the width it is asked to occupy. A hard-coded pixel threshold would break on
 * screens of another density.
 *
 * Pure function, outside any `Composable`, so it can be tested without
 * rendering.
 *
 * Height is deliberately ignored: the slot is 16/9 and cropping is horizontal
 * in the vast majority of cases; a wide enough image is defined enough
 * regardless of its height. Including height would treat perfectly sharp
 * banners as "small".
 *
 * @param sourceWidthPx width of the received image. Zero or negative (unknown
 *   size, image still loading) triggers nothing: never blur on a guess.
 * @param slotWidthPx measured slot width, in screen pixels.
 */
internal fun needsUpscaling(sourceWidthPx: Int, slotWidthPx: Int): Boolean =
    sourceWidthPx > 0 && slotWidthPx > 0 && sourceWidthPx < slotWidthPx
