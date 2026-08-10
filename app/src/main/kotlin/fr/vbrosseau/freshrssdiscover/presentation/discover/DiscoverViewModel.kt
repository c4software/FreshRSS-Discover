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
 * Le flux en mode Liste (SPECS.md §4.3).
 *
 * Tout le moteur — pagination, rechargement, amorçage, marquage, avis — vit
 * dans [FeedSessionViewModel] : ce type ne fournit que le câblage Hilt et la
 * projection de la Liste, dont l'extrait est calibré sur trois lignes de carte
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
