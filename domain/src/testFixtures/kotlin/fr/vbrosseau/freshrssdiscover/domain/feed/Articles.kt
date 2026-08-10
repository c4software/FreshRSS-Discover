package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Article factories for tests.
 *
 * Every parameter has a default: a test exercising the shuffle does not care
 * about the summary, and forcing it to provide one would drown out what it
 * actually verifies.
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
