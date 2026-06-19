package com.lifelog.app.util

/** Largest entry count shown verbatim; anything above is capped to "$MAX_ENTRY_COUNT+". */
const val MAX_ENTRY_COUNT = 999

/**
 * Formats an entry count for display with pluralization and capping:
 *
 * - `0`        -> "0 entries"
 * - `1`        -> "1 entry"
 * - `25`       -> "25 entries"
 * - `999`      -> "999 entries"
 * - `> 999`    -> "999+ entries"
 *
 * Negative inputs are clamped to 0 defensively.
 */
fun formatEntryCount(count: Int): String = when {
    count > MAX_ENTRY_COUNT -> "$MAX_ENTRY_COUNT+ entries"
    count == 1 -> "1 entry"
    count <= 0 -> "0 entries"
    else -> "$count entries"
}
