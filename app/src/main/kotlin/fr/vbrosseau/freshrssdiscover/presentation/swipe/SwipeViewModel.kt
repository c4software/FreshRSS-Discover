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
 * Le flux, un article par écran (SPECS.md §4.8).
 *
 * Tout le moteur vit dans [FeedSessionViewModel] : mêmes pages, même marquage,
 * même fin de flux explicite qu'en Liste — c'est la promesse de SPECS.md §4.8.
 * Ce type ne fournit que le câblage Hilt et la projection du Balayage, dont
 * l'extrait plein écran monte à 1 400 caractères (SPECS.md §8, question 7). La
 * source d'observation de la visibilité, elle, est l'affaire de l'écran
 * ([pagerVisibility]).
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
