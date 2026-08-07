package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Ce que le cache local contient, tel que l'écran de réglages doit le montrer
 * (SPECS.md §6).
 *
 * **Deux nombres d'articles, et aucun octet.** Le poids du fichier de base est
 * accessible, mais il ne mesure pas le cache : SQLite ne rend pas ses pages au
 * système quand des lignes disparaissent, il les garde pour les réécrire. Une
 * purge laisserait donc le nombre de mégaoctets **inchangé**, et l'utilisateur
 * lirait cela comme une purge sans effet. Le poids réel de l'application est
 * par ailleurs déjà donné, correctement et en un seul endroit, par les réglages
 * de stockage d'Android : le redire ici n'ajouterait qu'une seconde source,
 * moins juste.
 *
 * [purgeableCount] est le chiffre qui manque partout ailleurs : il répond à la
 * seule question que pose un bouton « Purger » — *qu'est-ce que je perds ?* —
 * et il bouge à vue quand on appuie dessus.
 */
data class CacheStatus(
    /** Tous les articles conservés, lus comme non lus. */
    val articleCount: Int,
    /**
     * Ceux que la purge emporterait : **lus et synchronisés** (SPECS.md §5.4).
     *
     * Un article lu dont le marquage n'est pas encore parti n'en fait pas
     * partie — voir la garantie décrite sur [purgeReadArticles].
     */
    val purgeableCount: Int,
) {
    companion object {
        /** Cache vide : valeur initiale, avant toute lecture de la base. */
        val Empty: CacheStatus = CacheStatus(articleCount = 0, purgeableCount = 0)
    }
}

/**
 * Mesure et purge du cache local (SPECS.md §5.4, §6).
 *
 * Séparé de `SettingsRepository` : celui-ci décrit des préférences que
 * l'utilisateur choisit, celui-là un état de l'appareil qu'il constate. Les
 * réunir obligerait l'écran de réglages à ne plus savoir laquelle de ses deux
 * moitiés il observe.
 */
interface CacheRepository {
    /**
     * L'état du cache, observable.
     *
     * Un [Flow] et non une lecture ponctuelle : le nombre d'articles change
     * pendant que l'écran est ouvert — une purge manuelle le fait tomber, une
     * synchronisation en arrière-plan le fait monter — et un chiffre figé
     * ferait douter de la purge qu'on vient de déclencher.
     */
    fun observeCacheStatus(): Flow<CacheStatus>

    /**
     * Purge **maintenant** tout ce qui est lu et synchronisé, sans condition
     * d'ancienneté. Renvoie le nombre d'articles supprimés.
     *
     * C'est la purge manuelle de SPECS.md §6 : la même règle que la purge
     * automatique, seul le seuil d'ancienneté tombe. Elle ne peut donc emporter
     * ni un article non lu, ni un article dont le marquage attend encore d'être
     * transmis — ce qui est précisément ce qui la rend sûre à déclencher sans
     * confirmation.
     */
    suspend fun purgeReadArticles(): Int

    companion object {
        /**
         * Seuil d'ancienneté de la purge automatique — **7 jours**.
         *
         * Réponse à SPECS.md §8, question 3. Ce que le seuil arbitre : au-delà,
         * un article lu n'a plus aucun lecteur. En deçà, il en a deux.
         *
         * Le premier est le **défilement arrière**. Le flux est continu et sans
         * repère (SPECS.md §1) ; y remonter est le seul moyen de retrouver ce
         * qu'on a survolé la veille. Un seuil de 24 h ferait disparaître ce
         * passé entre deux lancements, et le trou serait visible. Une semaine
         * couvre le rythme réel d'usage — on revient le lundi et on retrouve
         * son flux là où on l'avait laissé le vendredi.
         *
         * Le second est la **mémoire du « déjà lu »** : c'est la table des
         * articles qui la porte (`upsertPreservingLocalReadState`). La condition
         * de synchronisation garantit qu'un article purgé est déjà connu du
         * serveur comme lu, donc que sa mémoire est ailleurs — mais elle ne
         * garantit rien sur les jours d'avance que le serveur conserve. Une
         * semaine laisse au serveur le temps de rendre la même réponse.
         *
         * Pourquoi pas 30 jours : le cache quadruplerait pour du contenu déjà
         * consommé, dont le seul lecteur serait un défilement arrière d'un mois
         * que personne ne fait. À 40 articles par page (SPECS.md §8, question 1)
         * et quelques pages par session, 7 jours plafonnent le cache à quelques
         * milliers d'articles.
         */
        val MaxAge: Duration = 7.days
    }
}
