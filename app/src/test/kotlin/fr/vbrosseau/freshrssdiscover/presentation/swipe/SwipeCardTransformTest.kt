package fr.vbrosseau.freshrssdiscover.presentation.swipe

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Les comparaisons sont faites à une tolérance près, comme dans
 * `SwipeVisibilityTest` et pour une raison voisine : une rotation nulle sort du
 * calcul sous la forme `-0,0`, que `Float.equals` distingue de `0,0` alors
 * qu'aucun rendu ne les distingue.
 */
private const val TOLERANCE = 1e-4f

/**
 * La géométrie de la pile de cartes, éprouvée sans rien rendre.
 *
 * Ce que ces tests protègent n'est pas l'esthétique — elle se regarde sur les
 * captures et sur l'appareil — mais les propriétés qu'aucune capture ne montre :
 * qu'à aucun instant du geste une carte ne devient invisible, renversée, ou
 * dessinée devant celle qui devrait la couvrir.
 */
class SwipeCardTransformTest {

    @Test
    fun aSettledCardIsLeftExactlyAsItIs() {
        val transform = swipeCardTransform(0f)

        assertEquals(0f, transform.translationXFraction, TOLERANCE)
        assertEquals(0f, transform.rotationDegrees, TOLERANCE)
        assertEquals(1f, transform.scale, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
    }

    @Test
    fun aCardOnItsWayOutLeansTowardsWhereItIsGoing() {
        // Le décalage positif dit « vers la gauche » : le sommet penche du même
        // côté, donc une rotation négative.
        val transform = swipeCardTransform(0.5f)

        assertTrue(transform.rotationDegrees < 0f, "inclinaison ${transform.rotationDegrees}")
    }

    @Test
    fun aCardOnItsWayOutKeepsThePagerMovement() {
        // Rien n'est ajouté au déplacement : c'est le pagineur qui la sort de
        // l'écran, et le doigt doit la sentir suivre.
        assertEquals(0f, swipeCardTransform(0.5f).translationXFraction, TOLERANCE)
    }

    @Test
    fun theCardUnderneathStaysCentredInsteadOfSlidingIn() {
        // Le pagineur l'a posée à +0,6 largeur ; on ajoute exactement l'opposé.
        assertEquals(-0.6f, swipeCardTransform(-0.6f).translationXFraction, TOLERANCE)
    }

    @Test
    fun theCardUnderneathGrowsAsItIsRevealed() {
        val hidden = swipeCardTransform(-1f).scale
        val halfway = swipeCardTransform(-0.5f).scale
        val arrived = swipeCardTransform(0f).scale

        assertTrue(hidden < halfway && halfway < arrived, "$hidden < $halfway < $arrived")
        assertEquals(1f, arrived, TOLERANCE)
    }

    @Test
    fun theCardUnderneathNeverTiltsNorFades() {
        // Elle attend : la faire pencher ferait deux cartes en mouvement, et le
        // regard ne saurait plus laquelle il est en train de quitter.
        val transform = swipeCardTransform(-0.5f)

        assertEquals(0f, transform.rotationDegrees, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
    }

    @Test
    fun theFlyingCardIsAlwaysDrawnOverTheDeck() {
        // Vrai dans les deux sens : en arrière, c'est la carte précédente qui
        // revient par la gauche, décalage positif, et elle doit se reposer
        // **sur** la pile et non se glisser dessous.
        assertTrue(swipeCardTransform(0.4f).drawOrder > swipeCardTransform(-0.6f).drawOrder)
        assertTrue(swipeCardTransform(0.6f).drawOrder > swipeCardTransform(-0.4f).drawOrder)
    }

    @Test
    fun aCardNeverBecomesInvisibleWhileStillOnScreen() {
        // Une carte qui s'efface complètement avant d'avoir quitté le cadre se
        // dissout sur place, alors que le geste dit qu'on la met de côté.
        var offset = 0f
        while (offset <= 1f) {
            assertTrue(swipeCardTransform(offset).alpha >= EXPECTED_MIN_ALPHA, "à $offset")
            offset += 0.05f
        }
    }

    @Test
    fun aCardFadesSteadilyAsItLeaves() {
        val early = swipeCardTransform(0.2f).alpha
        val late = swipeCardTransform(0.8f).alpha

        assertTrue(early > late, "$early > $late")
    }

    @Test
    fun aCardBeyondTheScreenIsNotPushedFurtherThanTheEdgeValues() {
        // Le pagineur compose parfois au-delà d'une page : sans borne, la
        // rotation continuerait de croître et l'opacité passerait sous zéro.
        val far = swipeCardTransform(2.5f)

        assertEquals(swipeCardTransform(1f).rotationDegrees, far.rotationDegrees, TOLERANCE)
        assertEquals(swipeCardTransform(1f).alpha, far.alpha, TOLERANCE)
    }

    @Test
    fun aDeckCardFarBehindKeepsTheSmallestScaleRatherThanShrinkingAway() {
        val far = swipeCardTransform(-3f)

        assertEquals(swipeCardTransform(-1f).scale, far.scale, TOLERANCE)
        assertTrue(far.scale > 0f)
    }

    private companion object {
        /** L'opacité résiduelle annoncée par le module, reprise ici comme attente. */
        const val EXPECTED_MIN_ALPHA = 0.4f
    }
}
