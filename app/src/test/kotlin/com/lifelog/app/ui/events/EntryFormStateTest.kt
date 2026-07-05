package com.lifelog.app.ui.events

import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down [EntryFormState.toEventEntry] — the guard that keeps the entry
 * form from ever saving a foreign key it didn't load. The type id must come
 * from the loaded event type (editing from the Timeline used to pass 0L in and
 * corrupt the edited entry), and a form with no resolved type must not produce
 * an entry at all (deleted event behind a stale widget or notification).
 */
class EntryFormStateTest {

    private val bloodPressure = EventType(id = 42L, name = "Blood Pressure")

    @Test
    fun `edited entry keeps the type id it was loaded with`() {
        val state = EntryFormState(
            eventType = bloodPressure,
            existingEntryId = 7L,
            fieldValues = mapOf(1L to FieldValue.Numeric(120.0)),
            note = "after coffee",
            createdAt = 1_700_000_000_000L
        )

        val entry = state.toEventEntry()!!

        assertEquals(42L, entry.eventTypeId)
        assertEquals(7L, entry.id)
        assertEquals(mapOf<Long, FieldValue>(1L to FieldValue.Numeric(120.0)), entry.fieldValues)
        assertEquals("after coffee", entry.note)
        assertEquals(1_700_000_000_000L, entry.createdAt)
    }

    @Test
    fun `new entry carries the loaded type id and no existing id`() {
        val state = EntryFormState(eventType = bloodPressure)

        val entry = state.toEventEntry()!!

        assertEquals(42L, entry.eventTypeId)
        assertEquals(0L, entry.id)
    }

    @Test
    fun `no loaded event type means nothing to save`() {
        val state = EntryFormState(
            eventType = null,
            existingEntryId = 7L,
            fieldValues = mapOf(1L to FieldValue.Numeric(120.0))
        )

        assertNull(state.toEventEntry())
    }

    private val requiredWeight = EventField(id = 1L, name = "Weight", type = FieldType.NUMERIC, isRequired = true)
    private val optionalNote = EventField(id = 2L, name = "Notes", type = FieldType.TEXT, isRequired = false)
    private val requiredMood = EventField(id = 3L, name = "Mood", type = FieldType.CHOICE, isRequired = true)

    @Test
    fun `required fields with no value are flagged`() {
        val state = EntryFormState(
            eventType = bloodPressure.copy(fields = listOf(requiredWeight, optionalNote, requiredMood)),
            fieldValues = emptyMap()
        )

        assertEquals(setOf(1L, 3L), state.missingRequiredFieldIds())
    }

    @Test
    fun `a required field with a value is not flagged`() {
        val state = EntryFormState(
            eventType = bloodPressure.copy(fields = listOf(requiredWeight, optionalNote, requiredMood)),
            fieldValues = mapOf(
                1L to FieldValue.Numeric(70.0),
                3L to FieldValue.Choice("Good")
            )
        )

        assertTrue(state.missingRequiredFieldIds().isEmpty())
    }

    @Test
    fun `optional fields are never flagged`() {
        val state = EntryFormState(
            eventType = bloodPressure.copy(fields = listOf(optionalNote)),
            fieldValues = emptyMap()
        )

        assertTrue(state.missingRequiredFieldIds().isEmpty())
    }

    @Test
    fun `no loaded event type has nothing to flag`() {
        val state = EntryFormState(eventType = null)

        assertTrue(state.missingRequiredFieldIds().isEmpty())
    }
}
