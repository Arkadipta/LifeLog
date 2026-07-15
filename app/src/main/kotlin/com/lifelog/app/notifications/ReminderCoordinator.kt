package com.lifelog.app.notifications

import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.RecurrenceCalculator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bulk re-arm: makes the OS alarm state match the reminders table again after events that
 * invalidate it wholesale — boot, app update, database restore, timezone / clock changes, and
 * app-start recovery. A mutex serializes the passes because several can coincide (the boot
 * broadcast and the first app start, for instance) and interleaving persist+arm for the same
 * reminder could leave the armed alarm and the stored trigger disagreeing.
 *
 * Per-reminder arming (create/edit/toggle/snooze) still lives with its call sites; migrate
 * those here as they are next touched so every isActive/nextTriggerAt transition goes through
 * one place.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler
) {
    private val rearmMutex = Mutex()

    /**
     * Reconcile and re-arm every active reminder. Stored triggers go through
     * [RecurrenceCalculator.computeRescheduleTrigger] first, so stale epochs are recomputed
     * from their rules instead of all firing the moment they are armed, and — when
     * [clockChanged] — wall-clock rules are re-anchored to the new local time.
     */
    suspend fun rescheduleAll(clockChanged: Boolean = false) {
        rearmMutex.withLock {
            val now = System.currentTimeMillis()
            for (reminder in reminderRepository.getAllActive()) {
                val trigger = RecurrenceCalculator.computeRescheduleTrigger(
                    rule = reminder.recurrenceRule,
                    storedNextTriggerAt = reminder.nextTriggerAt,
                    now = now,
                    clockChanged = clockChanged
                )
                if (trigger != reminder.nextTriggerAt) {
                    reminderRepository.updateNextTrigger(reminder.id, trigger)
                }
                reminderScheduler.schedule(reminder.copy(nextTriggerAt = trigger))
            }
        }
    }

    /**
     * App-start safety net: re-arm everything when the system no longer holds our alarms.
     * A Google Auto Backup or device-transfer restore lands on a fresh install where the
     * restored rows have no PendingIntents, and a force-stop drops them wholesale — neither
     * delivers any broadcast to re-arm from, so probing on start is the only reliable hook.
     * A [databaseRestored] start re-arms unconditionally: the in-app restore just swapped the
     * whole database underneath whatever was armed.
     */
    suspend fun ensureArmedOnAppStart(databaseRestored: Boolean) {
        val armLost = databaseRestored ||
            reminderRepository.getAllActive().any { !reminderScheduler.hasAlarmToken(it.id) }
        if (armLost) rescheduleAll()
    }

    /**
     * Event-type deletion: reminders carry no FK to event_types, so the link must be
     * severed here or rows keep pointing at the dead id (a TIME_SINCE_LAST reminder can
     * then never reset — only a logged entry for its event does that). Every linked
     * reminder is unlinked (eventTypeId → NULL, the editor's "All Events (Global)"
     * state) and deactivated in one transaction, then its armed alarm cancelled.
     * Deactivation rather than deletion: the user's title/message/schedule survive,
     * visibly switched off in the Reminders list. Callers run this BEFORE deleting the
     * event row, so no reminder references a dead id even if the process dies between
     * the two steps.
     */
    suspend fun detachFromEventType(eventTypeId: Long) {
        rearmMutex.withLock {
            reminderRepository.detachFromEventType(eventTypeId).forEach { id ->
                reminderScheduler.cancel(id)
            }
        }
    }
}
