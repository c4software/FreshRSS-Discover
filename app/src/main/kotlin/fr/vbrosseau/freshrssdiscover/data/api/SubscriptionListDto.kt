package fr.vbrosseau.freshrssdiscover.data.api

import kotlinx.serialization.Serializable

/**
 * `subscription/list` response as FreshRSS produces it (docs/freshrss-api.md
 * §3.1).
 *
 * Only consumed fields are declared, as for [StreamContentsDto]: categories,
 * icon and priority are in the response but nothing here reads them.
 */
@Serializable
internal data class SubscriptionListDto(
    val subscriptions: List<SubscriptionDto> = emptyList(),
)

@Serializable
internal data class SubscriptionDto(
    /** `feed/<decimal>` — the form `subscription/edit` expects back on removal. */
    val id: String = "",
    val title: String = "",
    /** The feed's own address, the one the user subscribed with. */
    val url: String = "",
)
