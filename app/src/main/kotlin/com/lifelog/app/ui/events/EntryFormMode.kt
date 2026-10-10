package com.lifelog.app.ui.events

/**
 * What the entry form is doing: logging a new entry for a known event type, or
 * editing an existing entry. In [Edit] mode the event type is resolved from the
 * loaded entry itself, so edit-mode callers never have to supply — or fake — a
 * type id for an entry they only know by id.
 */
sealed interface EntryFormMode {
    data class New(val eventTypeId: Long) : EntryFormMode
    data class Edit(val entryId: Long) : EntryFormMode
}
