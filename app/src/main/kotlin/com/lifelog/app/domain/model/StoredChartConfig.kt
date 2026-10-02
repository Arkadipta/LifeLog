package com.lifelog.app.domain.model

/**
 * A chart-config row as it was read from the database. Chart settings persist
 * as one JSON column, so a row can stop decoding entirely (hand-edited or
 * damaged backup, restore across an incompatible config schema). Such a row
 * surfaces as [Unreadable] — keeping its slot in the analytics carousel so the
 * user can see it and delete it — rather than crashing the screen or silently
 * vanishing while still occupying the database.
 */
sealed interface StoredChartConfig {
    /** Room primary key — the only column still trusted on an unreadable row. */
    val id: String

    data class Readable(val config: ChartConfig) : StoredChartConfig {
        override val id: String get() = config.id
    }

    data class Unreadable(override val id: String) : StoredChartConfig
}
