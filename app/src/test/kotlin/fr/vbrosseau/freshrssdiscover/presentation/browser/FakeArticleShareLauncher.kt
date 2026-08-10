package fr.vbrosseau.freshrssdiscover.presentation.browser

/**
 * [ArticleShareLauncher] fake that records what it is asked to send.
 *
 * [sharedTexts] is what allows asserting that a refused link triggered
 * nothing: checking the returned result would not suffice, an intent could
 * still go out.
 */
internal class FakeArticleShareLauncher : ArticleShareLauncher {
    val sharedTexts = mutableListOf<String>()

    override fun share(text: String) {
        sharedTexts += text
    }
}
