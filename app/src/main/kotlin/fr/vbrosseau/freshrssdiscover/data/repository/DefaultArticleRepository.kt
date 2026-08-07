package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.StreamContentsDto
import fr.vbrosseau.freshrssdiscover.data.api.toArticlePage
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.di.IoDispatcher
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedResult
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nombre d'articles demandés par page.
 *
 * Tranche SPECS.md §8 question 1. Mesuré sur un flux réel : résumé médian de
 * 1 324 caractères, 90ᵉ centile à 4 379 — une page de 40 pèse donc environ
 * 55 ko. Assez d'avance pour que le défilement ne s'interrompe pas, sans
 * retarder le premier affichage.
 */
private const val PAGE_SIZE = 40

private const val HTTP_UNAUTHORIZED = 401

@Singleton
internal class DefaultArticleRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val network: NetworkAvailability,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {
    override suspend fun loadPage(cursor: PageCursor?): FeedResult<ArticlePage> = withContext(ioDispatcher) {
        val session = sessionStore.observeSession().first()

        if (session == null) {
            /*
             * Aucune session : l'aiguillage racine a déjà dû basculer vers
             * l'écran de connexion. Le signaler quand même plutôt que de
             * renvoyer une page vide, qui se lirait comme « plus d'articles ».
             */
            Outcome.Failure(FeedError.SessionExpired)
        } else {
            api.streamContents(
                address = session.server,
                token = session.token,
                pageSize = PAGE_SIZE,
                cursor = cursor,
            ).toFeedResult()
        }
    }

    private suspend fun ApiOutcome<StreamContentsDto>.toFeedResult(): FeedResult<ArticlePage> = when (this) {
        is ApiOutcome.Success -> Outcome.Success(value.toArticlePage())

        is ApiOutcome.HttpError -> httpFailure(status)

        is ApiOutcome.MalformedResponse -> Outcome.Failure(FeedError.Unexpected(detail))

        /*
         * La connectivité n'est lue qu'ici, au moment de l'échec : la constater
         * d'avance donnerait une réponse périmée, le réseau pouvant disparaître
         * pendant la requête — ce qui est exactement le cas à diagnostiquer.
         */
        is ApiOutcome.TransportError -> Outcome.Failure(
            if (network.isOnline()) FeedError.ServerUnreachable else FeedError.NoNetwork,
        )
    }

    /**
     * Un `401` sur une lecture signifie que le serveur refuse le jeton —
     * l'utilisateur a changé son mot de passe API.
     *
     * La session est effacée **ici**, ce qui fait basculer l'aiguillage racine
     * de lui-même : aucun écran n'a de redirection à déclencher (SPECS.md
     * §3.4). Le rappel de saisie, lui, survit : adresse et identifiant restent
     * préremplis.
     */
    private suspend fun httpFailure(status: Int): FeedResult<ArticlePage> = when (status) {
        HTTP_UNAUTHORIZED -> {
            sessionStore.invalidateTokens()
            Outcome.Failure(FeedError.SessionExpired)
        }

        else -> Outcome.Failure(FeedError.Unexpected("HTTP $status"))
    }
}
