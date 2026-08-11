package fr.vbrosseau.freshrssdiscover

/**
 * Single logcat tag for the whole read-marking path.
 *
 * The path crosses three layers — the screen detects, the repository queues and
 * transmits, the API sends — and no layer sees the others. A defect anywhere
 * along it produces the same symptom: an article read on the device comes back
 * unread from the server. Distinguishing "never detected" from "detected and
 * never sent" from "sent and ignored" therefore requires reading the three
 * layers in a single stream:
 *
 * ```
 * adb logcat -s ReadSync:V
 * ```
 *
 * One tag rather than one per class, and at file level rather than duplicated:
 * a tag that differs by a letter between two layers breaks exactly the
 * continuity this is for.
 *
 * These traces carry counts and article identifiers, never a token nor a
 * password. They are only emitted in debug builds, where `Timber` has a planted
 * tree ([FreshRssDiscoverApplication]).
 */
internal const val READ_SYNC_TAG = "ReadSync"
