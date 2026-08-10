package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.presentation.LoadingIndicator
import fr.vbrosseau.freshrssdiscover.presentation.feed.AfterRefreshSettles
import fr.vbrosseau.freshrssdiscover.presentation.feed.ArticleIllustration
import fr.vbrosseau.freshrssdiscover.presentation.feed.ArticleShareButton
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedCentered
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedEmptyMessage
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedFailureBlock
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedNotice
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedOfflineBanner
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRetryAction
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedStaleNotice
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing
import kotlinx.coroutines.flow.first

/**
 * Nombre d'articles restants sous lesquels la page suivante est demandée.
 *
 * SPECS.md §4.4 veut que le défilement ne s'interrompe pas : attendre le
 * dernier élément visible ferait apparaître l'indicateur à chaque fois. Cinq —
 * un huitième d'une page de 40 — laisse le temps d'un aller-retour réseau au
 * rythme de défilement ordinaire.
 */
private const val PREFETCH_DISTANCE = 5

/** Cible tactile minimale (SPECS.md §7.1) : Material s'arrête à 40 dp. */
private val MinTouchTarget = 48.dp

/**
 * Le flux Discover.
 *
 * Sans état métier : il affiche [uiState] et remonte les gestes, ce qui le rend
 * prévisualisable et testable sans graphe d'injection (AGENTS.md §9). Le seul
 * état qu'il tient est la position de défilement, qui n'appartient qu'à lui.
 *
 * @param onArticleShare **sans valeur par défaut**, contrairement aux avis et
 *   au rechargement : un `{}` implicite laisserait un bouton visible et inerte
 *   sur chaque carte, et rien ne le signalerait. L'oubli doit être une erreur
 *   de compilation.
 * @param onVisibilityChanged destinataire des relevés de visibilité (SPECS.md
 *   §4.5). **Nullable, et nul par défaut** : l'observation est une boucle
 *   périodique, et l'armer sans destinataire ferait tourner un minuteur pour
 *   jeter son résultat — c'est-à-dire dépenser de la batterie pour rien, et
 *   rendre les prévisualisations et les tests de rendu perpétuellement occupés.
 *   `null` dit donc « personne n'écoute », ce qu'un `{}` par défaut ne saurait
 *   exprimer.
 */
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onOfflineNoticeDismiss: () -> Unit = {},
    onStaleNoticeDismiss: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    ScrollToTopAfterRefresh(listState = listState, isRefreshing = uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Au-dessus du flux et non par-dessus : le bandeau informe, il ne
            // masque rien de ce qui reste lisible (SPECS.md §5.2).
            if (uiState.showsOfflineBanner) OfflineBanner()

            FeedBody(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onRefresh = onRefresh,
                onArticleClick = onArticleClick,
                onArticleShare = onArticleShare,
                modifier = Modifier.weight(1f),
                listState = listState,
                onVisibilityChanged = onVisibilityChanged,
            )

            /*
             * **Sous le flux et non par-dessus**, comme en mode Balayage : cet
             * avis dure jusqu'à ce qu'on l'acquitte ou qu'on recharge, et un
             * avis qui s'installe prend sa place dans la mise en page. Posé en
             * surimpression, il recouvrait la fin de ce qui est défilable.
             */
            if (uiState.showsStaleNotice) {
                StaleFeedNotice(onRefresh = onRefresh, onDismiss = onStaleNoticeDismiss)
            }
        }

        /*
         * Celui-ci reste **en surimpression**, et c'est la différence : il est
         * fugace — il répond à un geste qui vient d'échouer, et disparaît dès
         * qu'on l'acquitte. Décaler le flux pour l'afficher ferait bouger la
         * lecture à chaque ouverture refusée. Il ne rencontre jamais l'avis
         * d'ancienneté : il n'existe que hors ligne, où `showsStaleNotice` est
         * justement faux.
         */
        if (uiState.isOfflineOpenNoticeVisible) {
            OfflineOpenNotice(
                onDismiss = onOfflineNoticeDismiss,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** L'avis d'ancienneté partagé, sous les étiquettes de test de cet écran. */
@Composable
private fun StaleFeedNotice(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedStaleNotice(
        onRefresh = onRefresh,
        onDismiss = onDismiss,
        modifier = modifier.testTag(DiscoverTestTags.STALE_NOTICE),
        actionModifier = Modifier.testTag(DiscoverTestTags.STALE_NOTICE_REFRESH),
        dismissModifier = Modifier.testTag(DiscoverTestTags.STALE_NOTICE_DISMISS),
    )
}

@Composable
private fun FeedBody(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // Sans défaut : un `rememberLazyListState` de repli créerait un second état
    // de défilement, désynchronisé de celui que l'écran remonte au
    // retour-en-tête après rechargement.
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    val phase = uiState.phase

    when {
        uiState.articles.isNotEmpty() -> ArticleList(
            uiState = uiState,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onRefresh = onRefresh,
            onArticleClick = onArticleClick,
            onArticleShare = onArticleShare,
            modifier = modifier,
            listState = listState,
            onVisibilityChanged = onVisibilityChanged,
        )

        /*
         * Une session terminée est traitée comme une attente, et non comme une
         * erreur : le dépôt vient d'invalider le jeton, l'aiguillage racine
         * bascule de lui-même vers la connexion (SPECS.md §3.4). Afficher un
         * message ici reviendrait à commenter un écran sur le point de
         * disparaître.
         */
        phase == DiscoverPhase.InitialLoading ||
            phase == DiscoverPhase.SessionEnded -> FeedCentered(modifier) { LoadingIndicator() }

        phase is DiscoverPhase.Failed -> PullableMessage(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            FailureBlock(failure = phase.failure, onRetry = onRetry)
        }

        else -> PullableMessage(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            EmptyFeedMessage()
        }
    }
}

/**
 * Un écran sans article, tirable quand même (GOAL-025-T02).
 *
 * **Le tirage n'était armé que sur la liste**, au motif que les états sans
 * article avaient déjà leur reprise et qu'un geste de défilement ne se découvre
 * pas là où rien ne défile. L'usage a démenti la première moitié : le lecteur
 * qui a tout lu n'a pas d'erreur à reprendre, seulement un écran vide, et c'est
 * le tirage qu'il tente en premier — signalé par l'auteur. La seconde moitié
 * reste vraie, et c'est pourquoi le geste **s'ajoute** aux commandes existantes
 * plutôt que de les remplacer : le bouton de la barre de titre demeure, et
 * l'écran d'échec garde son « Réessayer ».
 *
 * **Une `LazyColumn` d'un seul élément**, là où un `Box` suffirait à
 * l'affichage : le tirage se détecte par le défilement imbriqué, et un cadre
 * qui ne défile pas n'en émet aucun — le geste serait inerte, ce qui est pire
 * que son absence. Une liste, elle, dispatche même lorsqu'elle n'a rien à
 * faire défiler. `fillParentMaxSize` rend au contenu exactement la hauteur du
 * cadre, donc le même centrage qu'avant : les captures ne bougent pas.
 *
 * Le premier chargement et la fin de session n'y passent pas : l'un a déjà sa
 * requête en vol, l'autre est sur le point de rendre l'écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullableMessage(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(DiscoverTestTags.PULLABLE_MESSAGE),
        ) {
            item(key = MESSAGE_KEY) {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                    content = { content() },
                )
            }
        }
    }
}

/**
 * Le flux et son geste de rafraîchissement (SPECS.md §4.6).
 *
 * Le tirage y est armé, et l'était longtemps **ici seulement** : les états sans
 * article avaient leur reprise, et un geste de défilement sur un écran qui n'en
 * propose pas se découvre mal. Un écran vide n'a pourtant rien à reprendre, et
 * le tirage est ce qu'on y tente d'abord — il est désormais armé là aussi, par
 * [PullableMessage] (GOAL-025-T02).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleList(
    uiState: DiscoverUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onArticleShare: (Long) -> Unit,
    // Sans défaut, comme `FeedBody` : l'état vient toujours de l'écran.
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
) {
    PrefetchNextPage(
        listState = listState,
        articleCount = uiState.articles.size,
        onLoadMore = onLoadMore,
    )

    if (onVisibilityChanged != null) {
        ObserveArticleVisibility(listState = listState, onVisibilityChanged = onVisibilityChanged)
    }

    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            /*
             * L'indicateur par défaut peint son disque en `surfaceContainer` :
             * posé sur une carte d'article, il s'y confond en thème clair, et
             * la capture l'a montré effaçant le titre qu'il recouvre. Le
             * conteneur primaire le détache des deux fonds, et son arc en
             * `onPrimaryContainer` reste lisible dans les deux thèmes.
             */
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = uiState.isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(DiscoverTestTags.LIST),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Clé stable : sans elle, l'insertion d'articles en tête (SPECS.md
            // §4.6) recomposerait toute la liste et déplacerait la lecture en
            // cours. Avec elle, la liste repositionne son premier élément
            // visible sur sa **clé** et non sur son rang : les articles insérés
            // au-dessus ne poussent donc pas la lecture vers le bas.
            items(items = uiState.articles, key = ArticleUiModel::id) { article ->
                ArticleCard(
                    article = article,
                    onClick = { onArticleClick(article.id) },
                    onShare = { onArticleShare(article.id) },
                )
            }

            item(key = FOOTER_KEY) {
                FeedFooter(uiState = uiState, onRetry = onRetry)
            }
        }
    }
}

/** Le bandeau hors ligne partagé (SPECS.md §5.2), sous l'étiquette de cet écran. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    FeedOfflineBanner(
        message = stringResource(R.string.discover_offline_banner),
        modifier = modifier.testTag(DiscoverTestTags.OFFLINE_BANNER),
    )
}

/**
 * L'ouverture refusée faute de réseau (SPECS.md §5.2).
 *
 * Le geste a échoué, la lecture continue : la bandelette explique pourquoi rien
 * ne s'est passé, et attend qu'on l'acquitte.
 */
@Composable
private fun OfflineOpenNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    FeedNotice(
        message = stringResource(R.string.discover_offline_open_blocked),
        actionLabel = stringResource(R.string.discover_offline_notice_dismiss),
        onAction = onDismiss,
        modifier = modifier.testTag(DiscoverTestTags.OFFLINE_NOTICE),
        actionModifier = Modifier.testTag(DiscoverTestTags.OFFLINE_NOTICE_DISMISS),
    )
}

/**
 * Demande la page suivante **avant** d'atteindre le bas (SPECS.md §4.4).
 *
 * `derivedStateOf` évite de relancer l'effet à chaque pixel parcouru : seul le
 * passage du seuil compte. Le nombre d'articles fait aussi partie de la clé —
 * une page plus courte que le seuil laisserait sinon la condition vraie sans
 * jamais rappeler le chargement, et le flux s'arrêterait sans le dire.
 *
 * **Rien ne part avant un vrai défilement**, et c'est la condition qui manquait.
 * Le lancement n'interroge plus le réseau (SPECS.md §5.1), mais le cache filtré
 * de ses articles lus tient parfois entièrement à l'écran : le bas était alors
 * atteint sans que personne n'ait bougé le doigt, et le chargement partait
 * quand même — la requête qu'on venait de retirer par la porte revenait par la
 * fenêtre. Constaté sur appareil : la date du dernier contact serveur changeait
 * encore à chaque ouverture.
 */
@Composable
private fun PrefetchNextPage(
    listState: LazyListState,
    articleCount: Int,
    onLoadMore: () -> Unit,
) {
    // Détecté sur la **position**, pas sur le geste : un défilement
    // programmatique en est un aussi, et `isScrollInProgress` le manquerait.
    // Mémorisé et non dérivé, car c'est un fait acquis — une fois l'utilisateur
    // entré dans le flux, la pagination suit sans qu'il ait à relancer un geste
    // à chaque page. Verrouillé par un effet et non écrit en composition : une
    // écriture d'instantané dans le corps du Composable est le motif à éviter.
    var hasScrolled by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0 ||
                listState.isScrollInProgress
        }.first { it }
        hasScrolled = true
    }

    val shouldLoadMore by remember(listState, articleCount) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && lastVisible >= articleCount - PREFETCH_DISTANCE
        }
    }

    LaunchedEffect(shouldLoadMore, articleCount, hasScrolled) {
        if (shouldLoadMore && hasScrolled) onLoadMore()
    }
}

/**
 * Relève périodiquement la visibilité des articles (SPECS.md §4.5).
 *
 * **Périodiquement, et non à chaque défilement.** Le seuil porte sur une durée
 * continue : un article immobile à l'écran ne produit aucun événement, et une
 * mesure déclenchée par le seul défilement ne le signalerait donc jamais. La
 * cadence retenue et sa justification sont dans
 * [VISIBILITY_SAMPLING_PERIOD_MILLIS].
 *
 * **`repeatOnLifecycle(RESUMED)` et non un simple `LaunchedEffect`.** Une
 * boucle liée à la seule composition continuerait de tourner écran éteint ou
 * application en arrière-plan : elle marquerait comme lus des articles que
 * personne ne regarde — un faux positif irréversible, puisque le marquage part
 * ensuite au serveur — tout en réveillant l'appareil cinq fois par seconde.
 * `RESUMED` plutôt que `STARTED` : c'est le seul état où l'écran est réellement
 * au premier plan et non simplement visible derrière une boîte de dialogue.
 *
 * Le calcul vit dans un effet, jamais dans le corps d'un Composable
 * (AGENTS.md §9) : il lit une disposition, il ne produit pas d'affichage.
 */
@Composable
private fun ObserveArticleVisibility(
    listState: LazyListState,
    onVisibilityChanged: (Map<ArticleId, Float>) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(listState, onVisibilityChanged, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sampleVisibility(
                visibility = { listState.layoutInfo.articleVisibility() },
                onVisibilityChanged = onVisibilityChanged,
            )
        }
    }
}

/**
 * Une carte par article.
 *
 * La carte n'est cliquable que si l'article a un lien : SPECS.md §4.7 demande
 * qu'un article sans lien exploitable ne le soit pas, et le donne à voir — d'où
 * la mention explicite plutôt qu'un simple clic sans effet.
 */
@Composable
private fun ArticleCard(
    article: ArticleUiModel,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchTarget)
        .testTag(DiscoverTestTags.card(article.id))

    if (article.isOpenable) {
        Card(onClick = onClick, modifier = cardModifier) { ArticleCardContent(article, onShare) }
    } else {
        Card(modifier = cardModifier) { ArticleCardContent(article, onShare) }
    }
}

@Composable
private fun ArticleCardContent(article: ArticleUiModel, onShare: () -> Unit) {
    Column {
        if (article.hasIllustration) {
            ArticleIllustration(imageUrl = article.imageUrl, testTag = DiscoverTestTags.ILLUSTRATION)
        }

        /*
         * `fillMaxWidth` n'est pas décoratif : sans lui, cette colonne épouse
         * son contenu, et l'`align(End)` du bouton de partage se range sur la
         * **largeur du texte** au lieu de celle de la carte. Le défaut ne se
         * voit que sur un article **sans illustration** et au texte court —
         * avec une illustration, celle-ci impose déjà la pleine largeur. C'est
         * exactement le travers qu'avait rencontré le fanion en GOAL-017-T02,
         * et il est revenu par la même porte.
         */
        /*
         * **Le bas ne porte aucune marge quand le pied a son bouton**, et c'est
         * lui qui la fournit : la cible tactile de 48 dp entoure un dessin de
         * 16, donc elle laisse déjà 16 points sous le trait — exactement la
         * marge des trois autres côtés. En ajouter faisait la bande que
         * l'auteur a signalée deux fois.
         *
         * C'est le plancher **sans rien céder** : descendre plus bas
         * demanderait de rétrécir la cible sous les 48 dp de SPECS.md §7.1.
         *
         * La carte sans lien, elle, garde sa marge : sans bouton pour la
         * porter, sa dernière ligne toucherait le bord.
         */
        val bottomPadding = if (article.isOpenable) Spacing.none else Spacing.md

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = bottomPadding),
            /*
             * **Aucun espacement entre le titre et l'extrait** : l'interligne
             * propre des deux styles les sépare déjà, et les 4 dp qui s'y
             * ajoutaient faisaient une respiration que l'auteur a trouvée
             * excessive sur appareil. Le pied, lui, reprend cet écart à son
             * compte — il change de nature, pas de paragraphe.
             */
        ) {
            ArticleCardTexts(article)
            ArticleCardFooter(article = article, onShare = onShare)
        }
    }
}

/**
 * Le pied de carte : provenance à gauche, partage à droite (GOAL-023).
 *
 * **Les deux tenaient chacun une ligne**, la source et la date en tête de
 * carte, le bouton seul en bas. Réunis, ils en rendent une au contenu — c'est
 * tout l'objet du resserrement demandé par l'auteur.
 *
 * Le titre devient du même coup la première chose lue, ce qu'un lecteur d'écran
 * gagne autant que l'œil : la provenance ne s'annonce plus avant le sujet.
 *
 * `weight(1f)` sur le texte, et non un espaceur : c'est lui qui doit céder
 * quand le nom du flux est long, en s'écourtant d'une ellipse, plutôt que de
 * pousser la commande hors de la carte.
 *
 * Le clic du bouton ne remonte pas à la carte : un `IconButton` consomme le
 * sien, et l'ouverture de l'article n'est donc pas déclenchée par un appui sur
 * le partage.
 */
@Composable
private fun ArticleCardFooter(
    article: ArticleUiModel,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * **Pas de `heightIn` ici**, et c'est un retour en arrière assumé. Il avait
     * été posé pour que le pied d'un article sans lien — donc sans bouton —
     * tienne la même hauteur que les autres. Le prix était visible sur
     * appareil : la cible de 48 dp du bouton, centrée sur une ligne de texte de
     * 16, laissait sous elle une bande vide que l'auteur a signalée. Entre un
     * gabarit régulier et une carte resserrée, c'est le resserrement qui a été
     * demandé.
     */
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.discover_article_meta,
                article.feedTitle,
                article.publishedAt.label(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (article.isOpenable) {
            ArticleShareButton(
                onShare = onShare,
                testTag = DiscoverTestTags.share(article.id),
            )
        }
    }
}

@Composable
private fun ColumnScope.ArticleCardTexts(article: ArticleUiModel) {
    // La source et la date ne sont plus ici mais en pied de carte
    // (`ArticleCardFooter`, GOAL-023) : le titre ouvre la carte.
    Text(
        text = article.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )

    if (article.excerpt.isNotBlank()) {
        Text(
            text = article.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (!article.isOpenable) {
        Text(
            text = stringResource(R.string.discover_article_no_link),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DiscoverTestTags.NO_LINK),
        )
    }
}

/**
 * Ce qui suit le dernier article.
 *
 * La fin du flux y est **dite**, jamais seulement subie : une liste qui cesse
 * de s'allonger est indistinguable d'une panne (SPECS.md §4.4).
 */
@Composable
private fun FeedFooter(
    uiState: DiscoverUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = uiState.phase

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (phase) {
            DiscoverPhase.LoadingMore -> LoadingIndicator()

            DiscoverPhase.EndOfFeed -> Text(
                text = stringResource(R.string.discover_end_of_feed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .testTag(DiscoverTestTags.END_OF_FEED),
            )

            /*
             * Hors ligne, le bandeau a déjà dit la cause en tête du flux : la
             * répéter en rouge sous le dernier article ferait de deux signaux
             * une alarme, alors que ce qui est affiché fonctionne (SPECS.md
             * §5.2). Seule la reprise demeure — c'est elle que SPECS.md §4.4
             * exige, pas la couleur.
             */
            is DiscoverPhase.Failed ->
                if (uiState.showsOfflineBanner) {
                    RetryAction(onRetry)
                } else {
                    FailureBlock(failure = phase.failure, onRetry = onRetry)
                }

            // Rien à dire : le flux continue, ou la session s'achève.
            DiscoverPhase.Idle,
            DiscoverPhase.InitialLoading,
            DiscoverPhase.SessionEnded,
            -> Unit
        }
    }
}

@Composable
private fun FailureBlock(
    failure: DiscoverFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedFailureBlock(
        failure = failure,
        retryLabel = stringResource(R.string.discover_retry),
        onRetry = onRetry,
        modifier = modifier.testTag(DiscoverTestTags.FAILURE),
        retryModifier = Modifier.testTag(DiscoverTestTags.RETRY),
    )
}

@Composable
private fun RetryAction(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FeedRetryAction(
        label = stringResource(R.string.discover_retry),
        onRetry = onRetry,
        modifier = modifier.testTag(DiscoverTestTags.RETRY),
    )
}

@Composable
private fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    FeedEmptyMessage(
        title = stringResource(R.string.discover_empty_title),
        body = stringResource(R.string.discover_empty_body),
        modifier = modifier.testTag(DiscoverTestTags.EMPTY),
    )
}

/** Clé du pied de liste, distincte de tout identifiant d'article. */
private const val FOOTER_KEY = "discover:footer"

/** Clé de l'unique élément d'un écran sans article. */
private const val MESSAGE_KEY = "discover:message"

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    AppTheme(dynamicColor = false) {
        DiscoverScreen(
            uiState = DiscoverUiState(
                articles = listOf(
                    ArticleUiModel(
                        id = 1L,
                        title = "Un titre d'article assez long pour tenir sur deux lignes",
                        feedTitle = "Le Monde",
                        publishedAt = RelativeTime.Hours(2),
                        excerpt = "Un extrait de l'article, écourté par l'application.",
                        imageUrl = "https://exemple.org/illustration.jpg",
                        isOpenable = true,
                    ),
                    ArticleUiModel(
                        id = 2L,
                        title = "Un article sans lien exploitable",
                        feedTitle = "Un flux mal formé",
                        publishedAt = RelativeTime.Days(3),
                        excerpt = "Sans illustration, et sans lien : la carte reste lisible.",
                        isOpenable = false,
                    ),
                ),
                phase = DiscoverPhase.EndOfFeed,
            ),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}

/** Le régime hors ligne : bandeau calme, cache intact, avis d'ouverture refusée. */
@Preview(showBackground = true)
@Composable
private fun DiscoverScreenOfflinePreview() {
    AppTheme(dynamicColor = false) {
        DiscoverScreen(
            uiState = DiscoverUiState(
                articles = listOf(
                    ArticleUiModel(
                        id = 1L,
                        title = "Un article venu du cache, toujours lisible sans réseau",
                        feedTitle = "Le Monde",
                        publishedAt = RelativeTime.Hours(6),
                        excerpt = "Le contenu enregistré reste consultable : rien n'est vidé.",
                        isOpenable = true,
                    ),
                ),
                phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
                isOffline = true,
                isOfflineOpenNoticeVisible = true,
            ),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        DiscoverScreen(
            uiState = DiscoverUiState(phase = DiscoverPhase.EndOfFeed),
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }
}

/**
 * Remonte en haut à la fin d'un tirer-pour-rafraîchir.
 *
 * SPECS.md §4.6 : le geste vide la liste et repart du début. Sans cette
 * remontée, l'utilisateur resterait à un rang qui ne désigne plus rien de ce
 * qu'il regardait — le contenu a été remplacé sous lui. Le front descendant
 * vit dans [AfterRefreshSettles], partagé avec le Balayage.
 */
@Composable
private fun ScrollToTopAfterRefresh(listState: LazyListState, isRefreshing: Boolean) {
    AfterRefreshSettles(isRefreshing) { listState.scrollToItem(0) }
}
