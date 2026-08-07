package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.Clock

/** Fraction de hauteur affichée à partir de laquelle un article compte comme regardé (SPECS.md §4.5). */
private const val DEFAULT_VISIBLE_FRACTION_THRESHOLD = 0.6f

/** Durée d'affichage continu exigée avant de conclure à une lecture (SPECS.md §4.5). */
private const val DEFAULT_CONTINUOUS_VISIBILITY_MILLIS = 1_000L

/**
 * Décide quand un article devient « lu » à partir de sa visibilité à l'écran.
 *
 * SPECS.md §4.5 fixe un **double seuil** : au moins 60 % de la hauteur affichée,
 * pendant au moins 1 seconde continue. Les deux sont nécessaires et aucun ne
 * suffit — la surface seule marquerait comme lus les articles traversés par un
 * défilement rapide, la durée seule marquerait un article à peine effleuré en
 * bord d'écran.
 *
 * Les deux valeurs sont des paramètres nommés : SPECS.md annonce qu'elles seront
 * ajustées à l'usage, et un réglage éparpillé en constantes serait impossible à
 * exposer dans les réglages (SPECS.md §6).
 *
 * Le composant est pur hors [clock] : il ne lit pas l'heure système, ne
 * déclenche aucun effet et ne connaît ni Android ni le réseau. C'est ce qui
 * rend la règle testable à la milliseconde près.
 *
 * Il n'est pas thread-safe : il est conçu pour être appelé depuis la boucle qui
 * observe la liste, c'est-à-dire toujours depuis le même fil.
 */
class ReadDetector(
    private val clock: Clock,
    private val visibleFractionThreshold: Float = DEFAULT_VISIBLE_FRACTION_THRESHOLD,
    private val continuousVisibilityMillis: Long = DEFAULT_CONTINUOUS_VISIBILITY_MILLIS,
) {
    /**
     * Instant auquel chaque article a franchi le seuil de surface, sans être
     * jamais repassé dessous depuis.
     *
     * Cette table est purgée à chaque appel des articles absents de
     * l'observation : sans cela, une session de défilement laisserait derrière
     * elle une entrée par article jamais revu.
     */
    private val visibleSince = mutableMapOf<ArticleId, Long>()

    /**
     * Articles déjà signalés.
     *
     * Ils sont conservés pour toute la vie du détecteur, car c'est la seule
     * façon de garantir qu'un article ne soit **jamais** signalé deux fois : un
     * article reste visible pendant des dizaines d'images de rendu après avoir
     * franchi le seuil, et le re-signaler produirait autant d'appels réseau
     * inutiles. Le coût est borné par ce que l'utilisateur a réellement lu — un
     * identifiant par article — et non par le nombre d'observations.
     */
    private val reported = mutableSetOf<ArticleId>()

    /**
     * Nombre d'articles dont une visibilité est en cours de chronométrage.
     *
     * Exposé pour que l'absence de fuite soit vérifiable : c'est la seule
     * partie de l'état interne qui croît avec le défilement plutôt qu'avec la
     * lecture.
     */
    val trackedArticleCount: Int
        get() = visibleSince.size

    /**
     * Prend en compte une nouvelle observation de visibilité.
     *
     * [visibility] décrit les articles actuellement à l'écran et la fraction de
     * leur hauteur affichée. Un article absent de la table est considéré comme
     * sorti de l'écran : son chronomètre est oublié, et s'il revient il repart
     * de zéro.
     *
     * Les deux seuils sont **inclusifs** : SPECS.md §4.5 dit « au moins 60 % »
     * et « au moins 1 seconde ». Exactement 60,0 % pendant exactement 1000 ms
     * suffit donc. Un seuil exclusif rendrait de surcroît la règle dépendante
     * de l'arrondi du calcul de fraction côté interface, où 0,6 n'est jamais
     * exactement représentable.
     *
     * @return les seuls articles qui viennent de franchir le seuil lors de cet
     *   appel — jamais ceux déjà signalés.
     */
    fun onVisibilityChanged(visibility: Map<ArticleId, Float>): Set<ArticleId> {
        val now = clock.nowEpochMillis()
        visibleSince.keys.retainAll(visibility.keys)
        val justRead =
            visibility
                .filterKeys { it !in reported }
                .filter { (id, fraction) -> hasReachedThreshold(id, fraction, now) }
                .keys
                .toSet()
        reported += justRead
        // Le chronomètre d'un article signalé n'a plus d'objet : le garder
        // ferait grossir la table tant que l'article reste à l'écran.
        visibleSince.keys.removeAll(justRead)
        return justRead
    }

    /**
     * Chronomètre la visibilité d'un article et dit s'il vient d'atteindre la
     * durée exigée.
     *
     * Retomber sous le seuil de surface efface le chronomètre plutôt que de le
     * suspendre : « continue » est la condition même que SPECS.md §4.5 pose.
     * Sans cet effacement, dix passages de 100 ms cumuleraient une seconde, ce
     * qui est exactement le défilement rapide que le seuil de durée écarte.
     */
    private fun hasReachedThreshold(
        id: ArticleId,
        fraction: Float,
        now: Long,
    ): Boolean {
        if (fraction < visibleFractionThreshold) {
            visibleSince.remove(id)
            return false
        }
        val since = visibleSince.getOrPut(id) { now }
        return now - since >= continuousVisibilityMillis
    }
}
