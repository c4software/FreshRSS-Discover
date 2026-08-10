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
 * Local article cache.
 *
 * Sole point of contact between the domain model and Room: entities do not
 * cross this boundary, otherwise a persistence annotation would end up
 * constraining the shape of `Article` (ARCHITECTURE.md §2.1).
 *
 * The clock is injected — the caching timestamp is observable by the purge,
 * and a test must be able to control it (AGENTS.md §2).
 */
@Singleton
internal class ArticleCache @Inject constructor(
    private val dao: ArticleDao,
    private val clock: Clock,
) {
    /**
     * Saves articles, preserving any read state already present locally.
     *
     * The local read state never regresses. A mark made offline is only
     * transmitted once the network returns (SPECS.md §5.2); until then, the
     * server still describes the article as unread. Overwriting the local
     * state with the server's would make what the user just read reappear in
     * the feed — the most visible regression a cache can produce. In the other
     * direction, an article read elsewhere (web UI, another device) arrives
     * read and becomes read here: "read" propagates, "unread" does not.
     */
    suspend fun save(articles: List<Article>) {
        val cachedAt = clock.nowEpochMillis()
        dao.upsertPreservingLocalReadState(articles.map { it.toEntity(cachedAt) })
    }

    /**
     * The [limit] most recent cached articles, read ones included.
     *
     * Serves the launch display (SPECS.md §5.1): the feed shows what it
     * already has and only queries the network if this list is empty. Read
     * articles are included so the set — hence the order — does not change
     * between openings; see `ArticleDao.observeArticles`.
     */
    fun observeArticles(limit: Int): Flow<List<Article>> =
        dao.observeArticles(limit).map { entities -> entities.map(ArticleEntity::toDomain) }

    /** What remains to read, for the daily reminder (SPECS.md §4.9). */
    suspend fun unreadArticles(limit: Int): List<Article> =
        dao.unreadArticles(limit).map(ArticleEntity::toDomain)

    /**
     * Marks articles as read locally, without transmitting anything.
     *
     * The optimistic half of marking (SPECS.md §4.5): the state changes
     * immediately, transmission follows. Going through the cache rather than
     * the DAO keeps the project rule — Room entities do not cross this
     * boundary, and nothing above needs to know a column name.
     */
    suspend fun markAsRead(ids: Collection<ArticleId>) {
        if (ids.isEmpty()) return
        dao.markAsRead(ids.map(ArticleId::value))
    }

    /**
     * Keeps only [ids] — the reload response — plus articles whose mark has
     * not yet been transmitted (SPECS.md §4.6, GOAL-027).
     *
     * The empty-list branch is not stylistic caution: Room generates no
     * parameter for an empty list, `NOT IN ()` is not valid SQL, and the empty
     * case is precisely the reader who has read everything — the more frequent
     * of the two.
     */
    suspend fun retainOnly(ids: Collection<ArticleId>) {
        if (ids.isEmpty()) {
            dao.deleteAllExceptPendingMarks()
        } else {
            dao.deleteExcept(ids.map(ArticleId::value))
        }
    }

    /** Empties the cache. Called on logout (SPECS.md §3.5). */
    suspend fun clear() {
        dao.deleteAll()
    }

    /**
     * Purges read and synchronized articles cached for longer than [maxAge].
     *
     * Returns the number of deleted rows. Two categories are always spared
     * (SPECS.md §5.4): unread articles, which are the application's very
     * content, and those whose mark is still awaiting transmission — deleting
     * them would make what the user just read reappear as unread. See
     * `ArticleDao.deleteReadCachedBefore`.
     */
    suspend fun purgeReadOlderThan(maxAge: Duration): Int =
        dao.deleteReadCachedBefore(clock.nowEpochMillis() - maxAge.inWholeMilliseconds)

    /**
     * Manual purge: the same rule, without the age condition.
     *
     * An explicitly infinite threshold rather than `purgeReadOlderThan(ZERO)`:
     * the latter compares to the present instant and would miss exactly the
     * articles saved within the same millisecond — that is, on a freshly
     * filled database, everything the user sees. A button announcing 812
     * articles and deleting none has no acceptable explanation.
     */
    suspend fun purgeAllRead(): Int = dao.deleteReadCachedBefore(Long.MAX_VALUE)

    /**
     * What the cache contains, and what a purge would remove.
     *
     * The two counters are combined here rather than exposed separately: the
     * screen shows them side by side, and two distinct flows would pass
     * through a transient state where the total has already dropped while the
     * purgeable count has not yet moved.
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
