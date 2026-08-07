package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedErrorTest {
    /**
     * Constate que le jeu de causes reste **plus court** que celui de
     * l'authentification.
     *
     * Y ajouter « API désactivée » ou « identifiants refusés » compilerait, et
     * obligerait ensuite chaque appelant à traiter des cas qui ne peuvent plus
     * survenir une fois la session ouverte.
     */
    @Test
    fun everyCauseIsDistinct() {
        val causes: List<FeedError> =
            listOf(
                FeedError.NoNetwork,
                FeedError.ServerUnreachable,
                FeedError.SessionExpired,
                FeedError.Unexpected("peu importe"),
            )

        assertEquals(causes.size, causes.distinct().size)
    }

    @Test
    fun anExpiredSessionIsACauseOfItsOwn() {
        // Ce n'est pas une erreur de lecture mais une fin de session : le dépôt
        // l'accompagne d'une invalidation, et l'aiguillage racine ramène à
        // l'écran de connexion. La confondre avec « serveur injoignable »
        // laisserait l'utilisateur réessayer indéfiniment.
        assertTrue(FeedError.SessionExpired != FeedError.ServerUnreachable)
    }

    @Test
    fun theTechnicalMessageSurvivesTransport() {
        assertEquals("HTTP 500", (FeedError.Unexpected("HTTP 500")).technicalMessage)
    }
}
