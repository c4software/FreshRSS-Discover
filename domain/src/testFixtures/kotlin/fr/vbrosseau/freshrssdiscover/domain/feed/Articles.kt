package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Fabriques d'articles pour les tests.
 *
 * Tout a une valeur par défaut : un test qui éprouve le mélange n'a que faire
 * du résumé, et l'obliger à en fournir un noierait ce qu'il vérifie.
 */
fun article(
    id: Long = 1L,
    title: String = "Un titre",
    url: String? = "https://exemple.org/article-$id",
    publishedAtEpochSeconds: Long = 1_700_000_000L,
    summary: String = "Un extrait.",
    imageUrl: String? = null,
    author: String? = null,
    feed: FeedRef = feedRef(),
    isRead: Boolean = false,
): Article =
    Article(
        id = ArticleId(id),
        title = title,
        url = url,
        publishedAtEpochSeconds = publishedAtEpochSeconds,
        summary = summary,
        imageUrl = imageUrl,
        author = author,
        feed = feed,
        isRead = isRead,
    )

fun feedRef(
    id: String = "feed/1",
    title: String = "Un flux",
): FeedRef = FeedRef(id, title)
