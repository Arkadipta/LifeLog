package com.lifelog.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-database facts M14 rests on. Deleting an event type CASCADE-deletes the
 * three child tables that declare a foreign key — but `reminders` deliberately has
 * none, so its rows survive still pointing at the dead id. That surviving row is
 * what [com.lifelog.app.data.db.dao.ReminderDao.detachFromEventType] exists to
 * clean up; the tests below pin both halves.
 */
@RunWith(AndroidJUnit4::class)
class EventTypeDeleteDbTest {

    private lateinit var db: LifeLogDatabase

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LifeLogDatabase::class.java
        ).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun seedEventType(name: String): Long =
        db.eventTypeDao().insert(EventTypeEntity(name = name, colorArgb = 0xFF888888.toInt()))

    @Test
    fun deletingEventType_cascadesChildren_butLeavesReminderPointingAtDeadId() = runBlocking {
        val typeId = seedEventType("Blood Pressure")
        db.eventFieldDao().insert(EventFieldEntity(eventTypeId = typeId, name = "Systolic", type = "NUMERIC"))
        db.eventEntryDao().insert(EventEntryEntity(eventTypeId = typeId))
        db.chartConfigDao().upsert(ChartConfigEntity(id = "chart-1", eventTypeId = typeId, configJson = "{}"))
        val reminderId = db.reminderDao().insert(ReminderEntity(eventTypeId = typeId, title = "Take BP"))

        db.eventTypeDao().deleteById(typeId)

        assertTrue(db.eventFieldDao().getByEventType(typeId).isEmpty())
        assertTrue(db.eventEntryDao().getAllForExport(typeId).isEmpty())
        assertTrue(db.chartConfigDao().getAll().none { it.eventTypeId == typeId })

        // No FK on reminders: the row survives, active, referencing the dead id.
        val orphan = db.reminderDao().getById(reminderId)
        assertEquals(typeId, orphan?.eventTypeId)
        assertEquals(true, orphan?.isActive)
    }

    @Test
    fun detachFromEventType_detachesExactlyTheLinkedRows_andReturnsTheirIds() = runBlocking {
        val doomed = seedEventType("Doomed")
        val other = seedEventType("Other")
        val dao = db.reminderDao()

        val linkedActive = dao.insert(ReminderEntity(eventTypeId = doomed, title = "linked active"))
        val linkedInactive = dao.insert(ReminderEntity(eventTypeId = doomed, title = "linked inactive", isActive = false))
        val otherActive = dao.insert(ReminderEntity(eventTypeId = other, title = "other event"))
        val globalActive = dao.insert(ReminderEntity(eventTypeId = null, title = "global"))

        val detached = dao.detachFromEventType(doomed)

        assertEquals(setOf(linkedActive, linkedInactive), detached.toSet())

        // Both formerly-linked rows: unlinked and off.
        for (id in listOf(linkedActive, linkedInactive)) {
            val row = dao.getById(id)!!
            assertNull(row.eventTypeId)
            assertFalse(row.isActive)
        }
        // Rows of other events and global rows are untouched.
        val untouched = dao.getById(otherActive)!!
        assertEquals(other, untouched.eventTypeId)
        assertTrue(untouched.isActive)
        val global = dao.getById(globalActive)!!
        assertNull(global.eventTypeId)
        assertTrue(global.isActive)
    }

    @Test
    fun observeActiveCountByEventType_countsOnlyActiveLinkedRows() = runBlocking {
        val typeId = seedEventType("Counted")
        val dao = db.reminderDao()
        dao.insert(ReminderEntity(eventTypeId = typeId, title = "active 1"))
        dao.insert(ReminderEntity(eventTypeId = typeId, title = "active 2"))
        dao.insert(ReminderEntity(eventTypeId = typeId, title = "inactive", isActive = false))
        dao.insert(ReminderEntity(eventTypeId = null, title = "global"))

        assertEquals(2, dao.observeActiveCountByEventType(typeId).first())
    }
}
