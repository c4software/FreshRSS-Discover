package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Time away past which coming back to the immersive feed reloads it
 * (SPECS.md §4.8).
 *
 * Thirty minutes, the author's figure (2026-08-25): what short-video feeds
 * do — a quick switch to another app keeps the page under the eyes, a
 * return after a coffee starts a fresh feed at the top. The List keeps its
 * quiet launch (SPECS.md §5.1); this rule is the immersive mode's alone.
 */
const val FOREGROUND_RELOAD_THRESHOLD_MILLIS: Long = 30 * 60 * 1_000L

/**
 * Whether a foregrounding of the immersive feed reloads it.
 *
 * `null` for [lastBackgroundedAtEpochMillis] means the feed has never been
 * backgrounded since it was created: a cold start, where the app was
 * killed, and the feed is reloaded so it opens on what is new. Otherwise
 * the time away decides, threshold inclusive — "at least" reads literally,
 * as for every threshold in this project.
 *
 * Pure, with the instant passed in: a rule that reads its own clock can
 * only be tested by waiting.
 */
fun reloadsOnForeground(
    lastBackgroundedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): Boolean =
    lastBackgroundedAtEpochMillis == null ||
        nowEpochMillis - lastBackgroundedAtEpochMillis >= FOREGROUND_RELOAD_THRESHOLD_MILLIS
