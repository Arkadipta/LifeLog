package com.lifelog.app.notifications

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.data.db.entity.ReminderEntity
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.Reminder
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
 * End-to-end contract of [ReminderCoordinator.detachFromEventType] against a real
 * device: the linked reminders' rows are unlinked + deactivated AND their armed
 * AlarmManager PendingIntents are cancelled, while reminders of other events keep
 * both their row and their alarm.
 *
 * Reminder ids start far above anything the app on this device could have created
 * (PendingIntent requestCodes are the reminder id, shared with the real app), so
 * the test can never disturb — or be fooled by — genuinely armed reminders.
 */
@RunWith(AndroidJUnit4::class)
class DetachFromEventTypeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: LifeLogDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var coordinator: ReminderCoordinator

    private val doomedReminderId = 9_100_001L
    private val survivorReminderId = 9_100_002L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LifeLogDatabase::class.java).build()
        scheduler = ReminderScheduler(context)
        coordinator = ReminderCoordinator(
            ReminderRepository(db.reminderDao(), db.eventTypeDao()),
            scheduler
        )
    }

    @After
    fun tearDown() {
        scheduler.cancel(doomedReminderId)
        scheduler.cancel(survivorReminderId)
        db.close()
    }

    @Test
    fun detach_cancelsArmedAlarmsOfLinkedReminders_only() = runBlocking {
        val doomedType = db.eventTypeDao().insert(EventTypeEntity(name = "Doomed", colorArgb = 1))
        val otherType = db.eventTypeDao().insert(EventTypeEntity(name = "Other", colorArgb = 2))
        val inAnHour = System.currentTimeMillis() + 60 * 60_000L

        db.reminderDao().insert(
            ReminderEntity(id = doomedReminderId, eventTypeId = doomedType, title = "doomed", nextTriggerAt = inAnHour)
        )
        db.reminderDao().insert(
            ReminderEntity(id = survivorReminderId, eventTypeId = otherType, title = "survivor", nextTriggerAt = inAnHour)
        )
        scheduler.schedule(Reminder(id = doomedReminderId, title = "doomed", nextTriggerAt = inAnHour))
        scheduler.schedule(Reminder(id = survivorReminderId, title = "survivor", nextTriggerAt = inAnHour))
        assertTrue(scheduler.hasAlarmToken(doomedReminderId))
        assertTrue(scheduler.hasAlarmToken(survivorReminderId))

        coordinator.detachFromEventType(doomedType)

        val detached = db.reminderDao().getById(doomedReminderId)!!
        assertNull(detached.eventTypeId)
        assertFalse(detached.isActive)
        assertFalse(scheduler.hasAlarmToken(doomedReminderId))

        val survivor = db.reminderDao().getById(survivorReminderId)!!
        assertEquals(otherType, survivor.eventTypeId)
        assertTrue(survivor.isActive)
        assertTrue(scheduler.hasAlarmToken(survivorReminderId))
    }
}
