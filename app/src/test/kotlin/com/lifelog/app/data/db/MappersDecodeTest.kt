package com.lifelog.app.data.db

import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.ReminderEntity
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.model.RecurrenceRule
import com.lifelog.app.domain.model.StoredChartConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the read-side mapper policy: decoding an entity out of the database
 * never throws. Serialized columns can arrive corrupt (damaged or hand-edited
 * backup, restore from a newer app version) and a bad row must degrade only
 * itself — pre-policy, any test in the "corrupt" sections below crashed the
 * mapper and with it every screen reading the table.
 */
class MappersDecodeTest {

    // ── EventFieldEntity ─────────────────────────────────────────────────────

    private fun fieldEntity(
        type: String = "CHOICE",
        optionsJson: String = """["Good","Bad"]"""
    ) = EventFieldEntity(
        id = 7,
        eventTypeId = 3,
        name = "Mood",
        type = type,
        optionsJson = optionsJson,
        unit = "pts",
        isRequired = true,
        sortOrder = 2
    )

    @Test
    fun `field with valid columns decodes fully`() {
        val field = fieldEntity().toDomain()
        assertEquals(7L, field.id)
        assertEquals(3L, field.eventTypeId)
        assertEquals("Mood", field.name)
        assertEquals(FieldType.CHOICE, field.type)
        assertEquals(listOf("Good", "Bad"), field.options)
        assertEquals("pts", field.unit)
        assertTrue(field.isRequired)
        assertEquals(2, field.sortOrder)
    }

    @Test
    fun `unknown or blank field type falls back to TEXT and keeps the rest of the row`() {
        val fromNewerVersion = fieldEntity(type = "DURATION").toDomain()
        assertEquals(FieldType.TEXT, fromNewerVersion.type)
        assertEquals("Mood", fromNewerVersion.name)
        assertEquals(listOf("Good", "Bad"), fromNewerVersion.options)

        assertEquals(FieldType.TEXT, fieldEntity(type = "").toDomain().type)
    }

    @Test
    fun `corrupt options json falls back to no options and keeps the declared type`() {
        val field = fieldEntity(optionsJson = "not json").toDomain()
        assertEquals(emptyList<String>(), field.options)
        assertEquals(FieldType.CHOICE, field.type)
    }

    // ── EventEntryEntity ─────────────────────────────────────────────────────

    private fun entryEntity(fieldValuesJson: String) = EventEntryEntity(
        id = 11,
        eventTypeId = 3,
        fieldValuesJson = fieldValuesJson,
        note = "after lunch",
        createdAt = 1_000L,
        updatedAt = 2_000L
    )

    @Test
    fun `entry with valid values decodes fully`() {
        val entry = entryEntity(
            """{"1":{"type":"numeric","value":42.0},"2":{"type":"text","value":"hi"}}"""
        ).toDomain()
        assertEquals(
            mapOf<Long, FieldValue>(
                1L to FieldValue.Numeric(42.0),
                2L to FieldValue.Text("hi")
            ),
            entry.fieldValues
        )
    }

    @Test
    fun `garbage values json loses only the values, never the entry`() {
        val entry = entryEntity("garbage").toDomain()
        assertEquals(emptyMap<Long, FieldValue>(), entry.fieldValues)
        assertEquals(11L, entry.id)
        assertEquals("after lunch", entry.note)
        assertEquals(1_000L, entry.createdAt)
        assertEquals(2_000L, entry.updatedAt)
    }

    @Test
    fun `a corrupt value drops only its own pair`() {
        val entry = entryEntity(
            """{"1":{"type":"numeric","value":42.0},"2":{"type":"duration","value":5},"3":5}"""
        ).toDomain()
        assertEquals(mapOf<Long, FieldValue>(1L to FieldValue.Numeric(42.0)), entry.fieldValues)
    }

    @Test
    fun `unparseable field-id keys are dropped, not collapsed onto one id`() {
        val entry = entryEntity(
            """{"abc":{"type":"text","value":"a"},"xyz":{"type":"text","value":"b"},"9":{"type":"numeric","value":1.0}}"""
        ).toDomain()
        // Pre-policy both bad keys collapsed to 0L, overwriting each other.
        assertFalse(entry.fieldValues.containsKey(0L))
        assertEquals(mapOf<Long, FieldValue>(9L to FieldValue.Numeric(1.0)), entry.fieldValues)
    }

    // ── ChartConfigEntity ────────────────────────────────────────────────────

    private fun chartEntity(configJson: String) = ChartConfigEntity(
        id = "row-id",
        eventTypeId = 3,
        configJson = configJson,
        sortOrder = 2,
        createdAt = 1_234L
    )

    @Test
    fun `chart with valid json is readable and DB columns override the json copies`() {
        val stored = chartEntity(
            """{"id":"json-id","eventTypeId":99,"sortOrder":9,"createdAt":9,
                "type":"LINE","numericFieldIds":[4],"title":"Weight"}"""
        ).toDomain()
        val config = (stored as StoredChartConfig.Readable).config
        assertEquals("row-id", config.id)
        assertEquals(3L, config.eventTypeId)
        assertEquals(2, config.sortOrder)
        assertEquals(1_234L, config.createdAt)
        assertEquals(ChartType.LINE, config.type)
        assertEquals(listOf(4L), config.numericFieldIds)
        assertEquals("Weight", config.title)
    }

    @Test
    fun `corrupt chart json becomes an unreadable stub carrying the row id`() {
        assertEquals(
            StoredChartConfig.Unreadable("row-id"),
            chartEntity("{broken").toDomain()
        )
    }

    @Test
    fun `unknown chart type inside valid json is unreadable too`() {
        assertEquals(
            StoredChartConfig.Unreadable("row-id"),
            chartEntity("""{"type":"SPIDER","numericFieldIds":[]}""").toDomain()
        )
    }

    // ── ReminderEntity (pre-existing fallbacks, pinned as part of the policy) ─

    @Test
    fun `corrupt reminder columns fall back to defaults instead of throwing`() {
        val reminder = ReminderEntity(
            id = 5,
            title = "Stretch",
            deliveryType = "SMOKE_SIGNAL",
            recurrenceRuleJson = "not json"
        ).toDomain()
        assertEquals(DeliveryType.NOTIFICATION, reminder.deliveryType)
        assertEquals(RecurrenceRule(), reminder.recurrenceRule)
        assertEquals("Stretch", reminder.title)
    }
}
