package fr.vbrosseau.freshrssdiscover.presentation.immersive

import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.reloadsOnForeground
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedSessionViewModel
import javax.inject.Inject

/**
 * The feed, one article per screen, flicked vertically (SPECS.md §4.8).
 *
 * The entire engine lives in [FeedSessionViewModel]: same pages, same marking,
 * same explicit end of feed as List mode, as SPECS.md §4.8 promises. This type
 * provides the Hilt wiring, the Immersive projection — whose full-screen
 * excerpt goes up to 1,400 characters (SPECS.md §8, question 7) — and the one
 * rule the List does not have: **coming back reloads** (GOAL-039-T02). The
 * visibility observation source belongs to the screen ([pagerVisibility]).
 */
@HiltViewModel
class ImmersiveViewModel @Inject constructor(
    articleRepository: ArticleRepository,
    readSyncRepository: ReadSyncRepository,
    settingsRepository: SettingsRepository,
    freshnessRepository: FeedFreshnessRepository,
    private val clock: Clock,
) : FeedSessionViewModel(
    articleRepository = articleRepository,
    readSyncRepository = readSyncRepository,
    settingsRepository = settingsRepository,
    freshnessRepository = freshnessRepository,
    clock = clock,
    project = { article, now -> article.toImmersiveUiModel(now) },
) {
    /**
     * Instant of the last backgrounding, `null` while the screen is shown.
     *
     * Kept in the ViewModel, which outlives the screen but not the process:
     * a killed app comes back with a fresh one, and that is exactly the cold
     * start the rule reloads on.
     */
    private var lastBackgroundedAtEpochMillis: Long? = null

    /** True once the screen has been shown: only the first showing is a cold start. */
    private var hasBeenShown = false

    /**
     * The screen comes to the foreground.
     *
     * A cold start, or a return after the domain's threshold, reloads and
     * opens on the first page — the short-video convention the author asked
     * for (2026-08-25); a shorter absence keeps the page under the eyes.
     * Otherwise the engine's own rule applies: an empty screen asks the
     * server once (GOAL-025).
     */
    fun onForeground() {
        val away = lastBackgroundedAtEpochMillis
        val due = !hasBeenShown || (away != null && reloadsOnForeground(away, clock.nowEpochMillis()))
        hasBeenShown = true
        lastBackgroundedAtEpochMillis = null
        if (due) refresh() else onScreenShown()
    }

    /** The screen leaves the foreground: the absence starts now. */
    fun onBackground() {
        lastBackgroundedAtEpochMillis = clock.nowEpochMillis()
    }
}
