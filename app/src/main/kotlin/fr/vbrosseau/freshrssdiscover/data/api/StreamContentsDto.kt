package fr.vbrosseau.freshrssdiscover.data.api

import kotlinx.serialization.Serializable

/**
 * `stream/contents` response as FreshRSS produces it.
 *
 * Almost everything is optional. `Entry::toGReader` only emits `author` or
 * `enclosure` when they exist, and their presence depends on the source RSS
 * feed, not on FreshRSS. A default value on every field is the only way not to
 * fail on a perfectly normal feed.
 *
 * Only consumed fields are declared: `ignoreUnknownKeys` lets the others
 * through, and docs/freshrss-api.md describes the full response. A field
 * declared "for the record" would be dead code (AGENTS.md §2).
 */
@Serializable
internal data class StreamContentsDto(
    val items: List<ItemDto> = emptyList(),
    /**
     * Absent when the stream is exhausted — the only end-of-stream signal
     * (docs/freshrss-api.md §3.5).
     */
    val continuation: String? = null,
)

@Serializable
internal data class ItemDto(
    /** Hexadecimal, prefixed with `tag:google.com,2005:reader/item/`. */
    val id: String = "",
    val title: String = "",
    /** Seconds since the Unix epoch. */
    val published: Long = 0L,
    /**
     * Carries the read state: `user/-/state/com.google/read` is present or not.
     *
     * There is no boolean field for this, and the category's absence means
     * unread — `…/unread` is never emitted in this mode.
     */
    val categories: List<String> = emptyList(),
    val canonical: List<LinkDto> = emptyList(),
    val alternate: List<LinkDto> = emptyList(),
    /** Content truncated by the server in this mode. */
    val summary: ContentDto? = null,
    /** Full content; absent from the mode used by `stream/contents`. */
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
     * May be just `image` rather than a full MIME type: when the source feed
     * specifies nothing, FreshRSS falls back to this value.
     */
    val type: String? = null,
)
