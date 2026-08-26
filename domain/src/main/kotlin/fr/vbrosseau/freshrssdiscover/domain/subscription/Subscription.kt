package fr.vbrosseau.freshrssdiscover.domain.subscription

/**
 * Identifies a subscription on the server.
 *
 * Numeric, not the `feed/<n>` stream name the API exchanges: that prefix is
 * a FreshRSS detail, and it stops in the data layer (ARCHITECTURE.md §2.1).
 */
@JvmInline
value class SubscriptionId(val value: Long)

/**
 * A feed the user is subscribed to (SPECS.md §6).
 *
 * Only what the settings show and what removal needs: the title, the
 * address, the identifier. Categories, icon and priority are the server's
 * business — managing them is out of scope (SPECS.md §2).
 */
data class Subscription(
    val id: SubscriptionId,
    val title: String,
    val url: String,
)
