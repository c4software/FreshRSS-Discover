package fr.vbrosseau.freshrssdiscover.presentation.swipe

import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedSessionViewModel
import javax.inject.Inject

/**
 * The feed, one article per screen (SPECS.md §4.8).
 *
 * The entire engine lives in [FeedSessionViewModel]: same pages, same marking,
 * same explicit end of feed as List mode, as SPECS.md §4.8 promises. This type
 * only provides the Hilt wiring and the Swipe projection, whose full-screen
 * excerpt goes up to 1,400 characters (SPECS.md §8, question 7). The
 * visibility observation source belongs to the screen ([pagerVisibility]).
 */
@HiltViewModel
class SwipeViewModel @Inject constructor(
    articleRepository: ArticleRepository,
    readSyncRepository: ReadSyncRepository,
    settingsRepository: SettingsRepository,
    freshnessRepository: FeedFreshnessRepository,
    clock: Clock,
) : FeedSessionViewModel(
    articleRepository = articleRepository,
    readSyncRepository = readSyncRepository,
    settingsRepository = settingsRepository,
    freshnessRepository = freshnessRepository,
    clock = clock,
    project = { article, now -> article.toSwipeUiModel(now) },
)
