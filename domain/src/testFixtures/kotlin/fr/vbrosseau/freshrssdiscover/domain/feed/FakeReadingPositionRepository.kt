package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Position de lecture en mémoire, pour les tests.
 *
 * [rememberedPositions] est un cumul et non la dernière valeur : ce qui compte
 * dans un test, c'est souvent qu'une écriture n'ait **pas** eu lieu à chaque
 * observation (SPECS.md §5.3).
 */
class FakeReadingPositionRepository(
    var position: ArticleId? = null,
) : ReadingPositionRepository {
    val rememberedPositions: MutableList<ArticleId> = mutableListOf()

    var forgetCallCount: Int = 0
        private set

    override suspend fun lastPosition(): ArticleId? = position

    override suspend fun remember(articleId: ArticleId) {
        position = articleId
        rememberedPositions += articleId
    }

    override suspend fun forget() {
        forgetCallCount++
        position = null
    }
}
