package fr.vbrosseau.freshrssdiscover.presentation.browser

/**
 * Double de [ArticleShareLauncher] qui enregistre ce qu'on lui demande
 * d'envoyer.
 *
 * [sharedTexts] est ce qui permet d'affirmer qu'un lien refusé n'a **rien**
 * déclenché : constater le résultat retourné ne suffirait pas, une intention
 * pourrait partir quand même.
 */
internal class FakeArticleShareLauncher : ArticleShareLauncher {
    val sharedTexts = mutableListOf<String>()

    override fun share(text: String) {
        sharedTexts += text
    }
}
