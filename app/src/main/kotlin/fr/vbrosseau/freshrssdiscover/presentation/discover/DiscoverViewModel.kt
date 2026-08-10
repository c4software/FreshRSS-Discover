package fr.vbrosseau.freshrssdiscover.presentation.discover

import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedSessionViewModel
import javax.inject.Inject

/**
 * The feed in List mode (SPECS.md §4.3).
 *
 * The whole engine (pagination, refresh, bootstrap, marking, notices) lives
 * in [FeedSessionViewModel]: this type only provides the Hilt wiring and the
 * List projection, whose excerpt is calibrated for three card lines
 * (SPECS.md §8, question 7).
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
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
    project = { article, now -> article.toUiModel(now) },
)
