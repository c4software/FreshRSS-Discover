package fr.vbrosseau.freshrssdiscover.data.local.room

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedRef
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Cache local des articles.
 *
 * Seul point de contact entre le modèle de domaine et Room : les entités ne
 * franchissent pas cette frontière, sinon une annotation de persistance
 * finirait par contraindre la forme d'`Article` (ARCHITECTURE.md §2.1).
 *
 * L'horloge est injectée — l'horodatage de mise en cache est une donnée
 * observable par la purge, un test doit pouvoir la piloter (AGENTS.md §2).
 */
@Singleton
internal class ArticleCache @Inject constructor(
    private val dao: ArticleDao,
    private val clock: Clock,
) {
    /**
     * Enregistre des articles, en conservant l'état lu déjà présent localement.
     *
     * Le choix : l'état lu local ne recule jamais. Un marquage parti hors ligne
     * n'est transmis qu'au retour du réseau (SPECS.md §5.2) ; jusque-là, le
     * serveur continue de décrire l'article comme non lu. Écraser l'état local
     * par le sien ferait réapparaître dans le flux ce que l'utilisateur vient
     * de lire — la régression la plus visible qu'un cache puisse produire. Dans
     * l'autre sens, un article lu ailleurs (interface web, autre appareil)
     * arrive lu et le devient ici : « lu » se propage, « non lu » non.
     */
    suspend fun save(articles: List<Article>) {
        val cachedAt = clock.nowEpochMillis()
        dao.upsertPreservingLocalReadState(articles.map { it.toEntity(cachedAt) })
    }

    /**
     * Les [limit] articles les plus récents du cache, **lus compris**.
     *
     * Sert l'affichage du lancement (SPECS.md §5.1) : le flux montre ce qu'il a
     * déjà, et n'interroge le réseau que si cette liste est vide. Les articles
     * lus y figurent pour que l'ensemble — donc l'ordre — ne change pas entre
     * deux ouvertures ; voir `ArticleDao.observeArticles`.
     */
    fun observeArticles(limit: Int): Flow<List<Article>> =
        dao.observeArticles(limit).map { entities -> entities.map(ArticleEntity::toDomain) }

    /** Ce qu'il reste à lire, pour le rappel quotidien (SPECS.md §4.9). */
    suspend fun unreadArticles(limit: Int): List<Article> =
        dao.unreadArticles(limit).map(ArticleEntity::toDomain)

    /**
     * Marque des articles comme lus **localement**, sans rien transmettre.
     *
     * C'est la moitié « optimiste » du marquage (SPECS.md §4.5) : l'état change
     * tout de suite, la transmission suit. Passer par le cache plutôt que par le
     * DAO garde la règle du projet — les entités Room ne franchissent pas cette
     * frontière, et rien au-dessus n'a à connaître le nom d'une colonne.
     */
    suspend fun markAsRead(ids: Collection<ArticleId>) {
        if (ids.isEmpty()) return
        dao.markAsRead(ids.map(ArticleId::value))
    }

    /**
     * Ne garde que [ids] — la réponse du rechargement — et ce dont le marquage
     * n'est pas encore parti (SPECS.md §4.6, GOAL-027).
     *
     * L'aiguillage sur la liste vide n'est pas une précaution de style : Room
     * n'engendre aucun paramètre pour une liste vide, `NOT IN ()` n'est pas du
     * SQL valide, et le cas vide est justement celui du lecteur qui a tout lu —
     * le plus fréquent des deux.
     */
    suspend fun retainOnly(ids: Collection<ArticleId>) {
        if (ids.isEmpty()) {
            dao.deleteAllExceptPendingMarks()
        } else {
            dao.deleteExcept(ids.map(ArticleId::value))
        }
    }

    /** Vide le cache. Appelé à la déconnexion (SPECS.md §3.5). */
    suspend fun clear() {
        dao.deleteAll()
    }

    /**
     * Purge les articles **lus et synchronisés** présents dans le cache depuis
     * plus de [maxAge].
     *
     * Renvoie le nombre de lignes supprimées. Deux catégories sont épargnées
     * quoi qu'il arrive (SPECS.md §5.4) : les articles **non lus**, qui sont le
     * contenu même de l'application, et ceux dont le marquage **attend encore
     * d'être transmis** — les effacer ferait réapparaître comme non lu ce que
     * l'utilisateur vient de lire. Voir `ArticleDao.deleteReadCachedBefore`.
     */
    suspend fun purgeReadOlderThan(maxAge: Duration): Int =
        dao.deleteReadCachedBefore(clock.nowEpochMillis() - maxAge.inWholeMilliseconds)

    /**
     * Purge manuelle : la même règle, **sans condition d'ancienneté**.
     *
     * Un seuil explicitement infini plutôt qu'un `purgeReadOlderThan(ZERO)` :
     * ce dernier compare à l'instant présent, et laisserait donc échapper
     * exactement les articles enregistrés dans la même milliseconde — c'est-à-
     * dire, sur une base fraîchement remplie, la totalité de ce que
     * l'utilisateur voit. Un bouton qui annonce 812 articles et n'en supprime
     * aucun n'a pas d'explication acceptable.
     */
    suspend fun purgeAllRead(): Int = dao.deleteReadCachedBefore(Long.MAX_VALUE)

    /**
     * Ce que le cache contient, et ce qu'une purge en retirerait.
     *
     * Les deux compteurs sont combinés ici plutôt que remontés séparément :
     * l'écran les affiche côte à côte, et deux flux distincts le feraient passer
     * par un état transitoire où le total a déjà baissé alors que le nombre
     * d'articles purgeables n'a pas encore bougé.
     */
    fun observeCacheStatus(): Flow<CacheStatus> =
        combine(dao.observeArticleCount(), dao.observePurgeableCount()) { total, purgeable ->
            CacheStatus(articleCount = total, purgeableCount = purgeable)
        }
}

private fun Article.toEntity(cachedAtEpochMillis: Long): ArticleEntity =
    ArticleEntity(
        id = id.value,
        title = title,
        url = url,
        publishedAtEpochSeconds = publishedAtEpochSeconds,
        summary = summary,
        imageUrl = imageUrl,
        author = author,
        feedId = feed.id,
        feedTitle = feed.title,
        isRead = isRead,
        cachedAtEpochMillis = cachedAtEpochMillis,
    )

private fun ArticleEntity.toDomain(): Article =
    Article(
        id = ArticleId(id),
        title = title,
        url = url,
        publishedAtEpochSeconds = publishedAtEpochSeconds,
        summary = summary,
        imageUrl = imageUrl,
        author = author,
        feed = FeedRef(id = feedId, title = feedTitle),
        isRead = isRead,
    )
