package com.lifelog.app.domain.model

/**
 * One row of an entry list: everything the Timeline and Event Detail lists need
 * to filter, group by day, and draw a card.
 *
 * Deliberately narrower than [EventEntry]. Only [fieldValues] costs anything to
 * produce — it comes out of a JSON column — so an implementation is free to
 * decode it on first read instead of up front. That is what keeps the Timeline
 * cheap: a screen holds every row that matches its filters, but pays the decode
 * only for the handful of rows Compose actually composes. Everything a list does
 * *without* drawing a card (searching notes and event names, filtering by tag,
 * grouping into days, counting positions for a date jump) reads only the plain
 * fields below and never touches [fieldValues].
 *
 * Consequently: **do not read [fieldValues] in a loop over a whole list.** Doing
 * so decodes the entire table on whatever thread you are on. Screens that
 * genuinely need every value (charts, value search, export) work from
 * [EventEntry] instead, which has them already decoded.
 */
interface EntryRow {
    val id: Long
    val eventTypeId: Long
    val eventTypeName: String
    val eventTypeCategory: String
    val eventTypeColor: Int
    val eventTypeIcon: String
    val note: String
    val createdAt: Long

    /** May be decoded lazily — see the warning in the interface doc. */
    val fieldValues: Map<Long, FieldValue>
}
