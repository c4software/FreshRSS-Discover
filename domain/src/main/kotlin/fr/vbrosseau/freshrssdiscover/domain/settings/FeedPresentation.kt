package fr.vbrosseau.freshrssdiscover.domain.settings

/**
 * How the feed is browsed (SPECS.md §4.8).
 *
 * Not an appearance setting. Content is strictly identical in both modes:
 * same articles, same shuffle, same order, same reading and loading rules.
 * Only the gesture changes, and with it the number of articles visible at
 * once. The type lives in the domain rather than the presentation layer
 * because it decides what the app reopens after being quit (SPECS.md §6), so
 * it is persisted, so it must exist where persistence is described.
 *
 * A closed enum rather than a boolean: "immersive enabled" would require
 * knowing the opposite is called "List", which nothing would indicate, and a
 * third mode would turn the type into a contradictory flag.
 */
enum class FeedPresentation {
    /** Vertical scrolling, several articles on screen as cards. */
    List,

    /** Vertical paging, one article at a time, filling the screen. */
    Immersive,

    ;

    companion object {
        /**
         * List, mandated by SPECS.md §4.8.
         *
         * The mode showing several articles at once: on first open it shows
         * what the feed is made of before requiring a gesture. Immersive is
         * chosen once the user knows what is being paged through.
         */
        val Default: FeedPresentation = List

        /**
         * Reads back a value from disk, never failing.
         *
         * `null` (nothing stored), an empty string, the name of a since-removed
         * mode (`Swipe`, replaced by [Immersive] in GOAL-038 without any
         * mapping, by the author's ruling), or a damaged preferences file all
         * fall back to [Default]: an unreadable presentation mode must not
         * prevent the app from starting. The corrected value is rewritten on
         * the user's next choice.
         *
         * The on-disk form is the name, not the `ordinal`: an ordinal would
         * tie the stored value to declaration order, and inserting a mode one
         * day would silently reopen the app in a different mode than the one
         * it was left in.
         */
        fun fromStoredName(raw: String?): FeedPresentation = entries.firstOrNull { it.name == raw } ?: Default
    }
}
