package fr.vbrosseau.freshrssdiscover.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse de `stream/contents`, telle que FreshRSS la produit.
 *
 * **Presque tout est facultatif.** `Entry::toGReader` n'émet `author`,
 * `enclosure` ou `origin.htmlUrl` que s'ils existent, et leur présence dépend
 * du flux RSS source, pas de FreshRSS. Une valeur par défaut sur chaque champ
 * est donc la seule façon de ne pas échouer sur un flux parfaitement normal.
 */
@Serializable
internal data class StreamContentsDto(
    val id: String = "",
    val updated: Long = 0L,
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
    /** Microsecondes, transmis comme chaîne — trois unités de temps coexistent. */
    val timestampUsec: String? = null,
)

@Serializable
internal data class LinkDto(
    val href: String = "",
    val type: String? = null,
)

@Serializable
internal data class ContentDto(
    val content: String = "",
)

@Serializable
internal data class OriginDto(
    val streamId: String = "",
    val title: String = "",
    val htmlUrl: String? = null,
)

@Serializable
internal data class EnclosureDto(
    val href: String = "",
    /**
     * Peut valoir `image` tout court, et non un type MIME complet : quand le
     * flux source ne précise rien, FreshRSS se rabat sur cette valeur.
     */
    val type: String? = null,
    val length: Long? = null,
)

/** Réponse de `subscription/list`. */
@Serializable
internal data class SubscriptionListDto(
    val subscriptions: List<SubscriptionDto> = emptyList(),
)

@Serializable
internal data class SubscriptionDto(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val htmlUrl: String = "",
    val iconUrl: String = "",
    @SerialName("frss:priority")
    val priority: String? = null,
)
