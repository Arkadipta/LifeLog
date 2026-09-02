package com.lifelog.app.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.domain.model.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * P1, both halves, against a real device database.
 *
 * [EventRepository.observeAllEventTypes] used to call `getByEventType` inside its
 * `combine` transform — one `event_fields` query per event type, re-run on every
 * emission of any combined flow, on a path six screens and widgets subscribe to.
 * And because the transform's inputs only observed `event_types`/`event_entries`,
 * a write that touched nothing but a field definition changed no observed table,
 * so the list served stale fields until an unrelated write happened to wake it.
 *
 * Both are pinned here rather than left to inspection: the query-count assertions
 * fail with the per-type read restored, and the re-emission test times out.
 */
@RunWith(AndroidJUnit4::class)
class EventTypeFieldsQueryTest {

    private lateinit var db: LifeLogDatabase
    private lateinit var repo: EventRepository

    /** Every statement Room executes, newest last. Cleared per measured window. */
    private val queryLog = CopyOnWriteArrayList<String>()

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LifeLogDatabase::class.java
        )
            // Direct executor, so a statement is recorded on the thread that ran it:
            // a count taken straight after an emission can't miss a late arrival.
            .setQueryCallback(
                RoomDatabase.QueryCallback { sql, _ -> queryLog += sql },
                Executor { it.run() }
            )
            .build()
        repo = EventRepository(db, db.eventTypeDao(), db.eventFieldDao(), db.eventEntryDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun seedType(name: String): Long =
        db.eventTypeDao().insert(EventTypeEntity(name = name, colorArgb = 0xFF888888.toInt()))

    private suspend fun seedField(typeId: Long, name: String, type: String, sortOrder: Int = 0, optionsJson: String = "[]"): Long =
        db.eventFieldDao().insert(
            EventFieldEntity(
                eventTypeId = typeId,
                name = name,
                type = type,
                optionsJson = optionsJson,
                sortOrder = sortOrder
            )
        )

    /**
     * Reads of the field table. Matches `FROM event_fields` rather than the bare
     * table name so Room's own `... ON event_fields` invalidation triggers, created
     * on first observation, aren't counted as queries.
     */
    private fun fieldReads(): List<String> = queryLog.filter { it.contains("FROM event_fields") }

    @Test
    fun observeAllEventTypes_readsAllFieldsInOneQuery_regardlessOfEventTypeCount() = runBlocking {
        repeat(5) { i ->
            val typeId = seedType("Type $i")
            seedField(typeId, "Field $i", "TEXT")
        }

        queryLog.clear()
        val types = withTimeout(TIMEOUT_MS) { repo.observeAllEventTypes().first() }

        assertEquals(5, types.size)
        assertTrue("each type should carry its own field", types.all { it.fields.size == 1 })
        assertEquals(
            "expected one whole-table read; got ${fieldReads().size}: ${fieldReads()}",
            1,
            fieldReads().size
        )
    }

    @Test
    fun observeAllEventTypes_groupsFieldsByOwningType_inSortOrder() = runBlocking {
        val meal = seedType("Meal")
        val walk = seedType("Walk")
        val fasting = seedType("Fasting") // defines no fields at all
        // Inserted out of order deliberately: the query's ORDER BY decides the
        // list order, not insertion order or row id.
        seedField(meal, "Sides", "MULTI_SELECT", sortOrder = 2)
        seedField(walk, "Distance", "NUMERIC", sortOrder = 0)
        seedField(meal, "Meal Type", "CHOICE", sortOrder = 0)
        seedField(meal, "Calories", "NUMERIC", sortOrder = 1)

        val byName = withTimeout(TIMEOUT_MS) { repo.observeAllEventTypes().first() }
            .associateBy { it.name }

        assertEquals(
            listOf("Meal Type", "Calories", "Sides"),
            byName.getValue("Meal").fields.map { it.name }
        )
        assertEquals(listOf("Distance"), byName.getValue("Walk").fields.map { it.name })
        // A fieldless type still appears, with an empty list — not dropped by the grouping.
        assertEquals(emptyList<String>(), byName.getValue("Fasting").fields.map { it.name })
        assertEquals(fasting, byName.getValue("Fasting").id)
    }

    @Test
    fun addingAFieldOption_reEmitsTheEventTypeList() = runBlocking {
        val meal = seedType("Meal")
        val fieldId = seedField(meal, "Meal Type", "CHOICE", optionsJson = """["Lunch"]""")

        val emissions = Channel<List<EventType>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            repo.observeAllEventTypes().collect { emissions.send(it) }
        }
        try {
            // Taking the first emission before writing proves the collector is live
            // and Room's invalidation observer registered, so the write can't race it.
            val before = withTimeout(TIMEOUT_MS) { emissions.receive() }
            assertEquals(listOf("Lunch"), before.single().fields.single().options)

            // Touches event_fields and nothing else — the exact write that used to
            // leave every subscriber holding the old option list.
            repo.addFieldOption(fieldId, "Brunch")

            val after = withTimeout(TIMEOUT_MS) { emissions.receive() }
            assertEquals(listOf("Lunch", "Brunch"), after.single().fields.single().options)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun getFieldsByEventType_keepsAnEntryPerRequestedId_inOneQuery() = runBlocking {
        val meal = seedType("Meal")
        val walk = seedType("Walk") // no fields
        seedField(meal, "Calories", "NUMERIC")

        queryLog.clear()
        // Repeats are what the timeline widget actually passes — one id per entry
        // it renders, so the same type recurs.
        val fields = repo.getFieldsByEventType(listOf(meal, walk, meal))

        assertEquals(setOf(meal, walk), fields.keys)
        assertEquals(listOf("Calories"), fields.getValue(meal).map { it.name })
        // Requested but fieldless: present with an empty list, as the widget's
        // `fieldsByType[id].orEmpty()` and the KDoc both expect.
        assertEquals(emptyList<String>(), fields.getValue(walk))
        assertEquals(
            "expected one whole-table read; got ${fieldReads().size}: ${fieldReads()}",
            1,
            fieldReads().size
        )
    }

    @Test
    fun getAllEventTypesForExport_carriesFieldsAndCounts_withoutPerTypeQueries() = runBlocking {
        val meal = seedType("Meal")
        val walk = seedType("Walk")
        seedType("Fasting") // no fields, no entries
        seedField(meal, "Calories", "NUMERIC")
        seedField(walk, "Distance", "NUMERIC")
        db.eventEntryDao().insert(EventEntryEntity(eventTypeId = meal))
        db.eventEntryDao().insert(EventEntryEntity(eventTypeId = meal))

        queryLog.clear()
        val exported = withTimeout(TIMEOUT_MS) { repo.getAllEventTypesForExport() }
            .associateBy { it.name }

        assertEquals(listOf("Calories"), exported.getValue("Meal").fields.map { it.name })
        assertEquals(2, exported.getValue("Meal").entryCount)
        assertEquals(0, exported.getValue("Walk").entryCount)
        // Zero-entry types are absent from the GROUP BY and must default to 0.
        assertEquals(0, exported.getValue("Fasting").entryCount)
        assertEquals(emptyList<String>(), exported.getValue("Fasting").fields.map { it.name })
        assertEquals(1, fieldReads().size)
        assertEquals(
            "entry counts should come from one GROUP BY, not one COUNT per type",
            1,
            queryLog.count { it.contains("COUNT(*)") }
        )
    }

    private companion object {
        /** Generous: these wait on real device I/O, not on a scheduler we control. */
        const val TIMEOUT_MS = 5_000L
    }
}
