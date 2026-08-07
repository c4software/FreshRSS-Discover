package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPositionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mémorise et restitue l'endroit où la lecture s'était arrêtée (SPECS.md §5.3).
 *
 * Séparé de `DiscoverViewModel` parce que c'est une préoccupation distincte,
 * avec son propre état et son propre cycle : le flux peut être rechargé,
 * rafraîchi, vidé, sans que la position cesse d'avoir un sens. Les réunir
 * faisait par ailleurs franchir à `DiscoverViewModel` le seuil de cohésion que
 * Detekt surveille — le signal était juste.
 *
 * C'est la contrepartie du tirer-pour-rafraîchir (§4.6), qui remonte
 * délibérément en haut : une fermeture, elle, n'est pas une demande.
 */
@HiltViewModel
class ReadingPositionViewModel @Inject constructor(
    private val repository: ReadingPositionRepository,
) : ViewModel() {
    private val _restoreToArticleId = MutableStateFlow<Long?>(null)

    /**
     * Article sur lequel remettre la liste, une seule fois.
     *
     * Un état et non un événement : l'écran peut être recréé — rotation, retour
     * d'arrière-plan — avant d'avoir eu l'occasion de s'y rendre.
     */
    val restoreToArticleId: StateFlow<Long?> = _restoreToArticleId.asStateFlow()

    /** Dernier article signalé en tête, pour n'écrire que sur changement. */
    private var lastRemembered: ArticleId? = null

    init {
        viewModelScope.launch {
            _restoreToArticleId.value = repository.lastPosition()?.value
        }
    }

    /**
     * Retient l'article en tête d'écran.
     *
     * N'écrit que sur **changement** : l'écran signale cinq fois par seconde, et
     * écrire autant solliciterait le disque pour rien. Un article nul est
     * ignoré — liste vide ou disposition pas encore mesurée — car l'écrire
     * effacerait une position parfaitement valable.
     */
    fun onFirstVisibleArticleChanged(articleId: ArticleId?) {
        if (articleId == null || articleId == lastRemembered) return

        lastRemembered = articleId
        viewModelScope.launch { repository.remember(articleId) }
    }

    /**
     * L'écran s'est rendu à la position : elle ne doit plus l'être.
     *
     * La garder en attente ferait sauter la liste au prochain chargement, alors
     * que l'utilisateur a déjà repris sa lecture.
     */
    fun onPositionRestored() {
        _restoreToArticleId.value = null
    }
}
