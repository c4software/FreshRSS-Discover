package fr.vbrosseau.freshrssdiscover.data.local.room

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedRef
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.Flow
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
     * Les [limit] articles les plus récents du cache.
     *
     * Sert l'affichage immédiat au lancement (SPECS.md §5.1) : le flux montre
     * ce qu'il a déjà avant que la moindre requête réseau n'aboutisse.
     */
    fun observeArticles(limit: Int): Flow<List<Article>> =
        dao.observeArticles(limit).map { entities -> entities.map(ArticleEntity::toDomain) }

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

    /** Vide le cache. Appelé à la déconnexion (SPECS.md §3.5). */
    suspend fun clear() {
        dao.deleteAll()
    }

    /**
     * Purge les articles **lus** présents dans le cache depuis plus de [maxAge].
     *
     * Renvoie le nombre de lignes supprimées. Les articles non lus ne sont
     * jamais purgés (SPECS.md §5.3) : ce sont eux le contenu de l'application,
     * les effacer priverait l'utilisateur de ce qu'il n'a pas encore vu.
     */
    suspend fun purgeReadOlderThan(maxAge: Duration): Int =
        dao.deleteReadCachedBefore(clock.nowEpochMillis() - maxAge.inWholeMilliseconds)
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
