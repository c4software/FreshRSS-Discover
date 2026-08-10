package fr.vbrosseau.freshrssdiscover.data.api

import kotlinx.serialization.Serializable

/**
 * Réponse de `stream/contents`, telle que FreshRSS la produit.
 *
 * **Presque tout est facultatif.** `Entry::toGReader` n'émet `author` ou
 * `enclosure` que s'ils existent, et leur présence dépend du flux RSS source,
 * pas de FreshRSS. Une valeur par défaut sur chaque champ est donc la seule
 * façon de ne pas échouer sur un flux parfaitement normal.
 *
 * Seuls les champs **consommés** sont déclarés : `ignoreUnknownKeys` fait
 * passer les autres, et docs/freshrss-api.md décrit la réponse entière. Un
 * champ déclaré « pour mémoire » serait du code mort (AGENTS.md §2).
 */
@Serializable
internal data class StreamContentsDto(
    val items: List<ItemDto> = emptyList(),
    /**
     * Absent lorsque le flux est épuisé — c'est le **seul** signal de fin
     * (docs/freshrss-api.md §3.5).
     */
    val continuation: String? = null,
)

@Serializable
internal data class ItemDto(
    /** Hexadécimal, préfixé de `tag:google.com,2005:reader/item/`. */
    val id: String = "",
    val title: String = "",
    /** Secondes depuis l'époque Unix. */
    val published: Long = 0L,
    /**
     * Porte l'état lu : `user/-/state/com.google/read` y figure ou non.
     *
     * Il n'existe **aucun** champ booléen pour cela, et l'absence de la
     * catégorie signifie « non lu » — `…/unread` n'est jamais émis dans ce mode.
     */
    val categories: List<String> = emptyList(),
    val canonical: List<LinkDto> = emptyList(),
    val alternate: List<LinkDto> = emptyList(),
    /** Contenu **tronqué** par le serveur dans ce mode. */
    val summary: ContentDto? = null,
    /** Contenu entier ; absent du mode employé par `stream/contents`. */
    val content: ContentDto? = null,
    val origin: OriginDto = OriginDto(),
    val author: String? = null,
    val enclosure: List<EnclosureDto> = emptyList(),
)

@Serializable
internal data class LinkDto(
    val href: String = "",
)

@Serializable
internal data class ContentDto(
    val content: String = "",
)

@Serializable
internal data class OriginDto(
    val streamId: String = "",
    val title: String = "",
)

@Serializable
internal data class EnclosureDto(
    val href: String = "",
    /**
     * Peut valoir `image` tout court, et non un type MIME complet : quand le
     * flux source ne précise rien, FreshRSS se rabat sur cette valeur.
     */
    val type: String? = null,
)
