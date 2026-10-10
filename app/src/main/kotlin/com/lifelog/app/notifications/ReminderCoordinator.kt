package com.lifelog.app.notifications

import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.RecurrenceCalculator
import com.lifelog.app.domain.model.RecurrenceType
import com.lifelog.app.domain.model.Reminder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of every `isActive` / `nextTriggerAt` transition and the OS alarm that
 * must match it. Two kinds of pass live here:
 *
 *  • **Per-reminder** — create/edit ([save]), fire ([onFired]), [snooze], [setActive],
 *    [delete], and the TIME_SINCE_LAST reset ([onEntryLogged]).
 *  • **Bulk** — [rescheduleAll] for the events that invalidate the OS alarm state wholesale
 *    (boot, app update, database restore, timezone / clock change) and [ensureArmedOnAppStart]
 *    for the ones that deliver no broadcast at all.
 *
 * One mutex serializes them all. That matters most where the two kinds meet: a bulk pass that
 * interleaved with a snooze used to read the pre-snooze row and re-arm the regular occurrence
 * on top of it.
 *
 * **The stored trigger is the whole truth.** `nextTriggerAt` means "when this reminder fires
 * next" — not "when its rule says it should" — because that stored epoch is all a reboot has
 * to rebuild from. Anything that defers a reminder (notably [snooze]) must therefore persist
 * the new time, not just hand it to the scheduler. Nothing is lost by overwriting: a recurring
 * rule regenerates its regular occurrence in [onFired] when the deferred alarm actually rings,
 * and no UI reads the column (the Reminders list describes the rule; the alarm screen's "Next"
 * label recomputes from it) — only the scheduler and the bulk reconcile do.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler
) {
    private val rearmMutex = Mutex()

    // ── Per-reminder transitions ──────────────────────────────────────────────

    /**
     * Persist a created or edited reminder and make the OS match it, returning the row id.
     *
     * [eventDateTime] seeds a TIME_SINCE_LAST countdown from the entry the user picked.
     * When the rule leaves nothing to fire — an already-elapsed TIME_SINCE_LAST — the alarm
     * is **cancelled** rather than left alone: an edit reuses the reminder's id, so the alarm
     * armed for the previous schedule survives otherwise and rings at the old time carrying
     * the new title. Same for editing a reminder that is currently switched off.
     */
    suspend fun save(reminder: Reminder, eventDateTime: Long? = null): Long = rearmMutex.withLock {
        val now = System.currentTimeMillis()
        val trigger = RecurrenceCalculator.computeInitialTrigger(
            rule = reminder.recurrenceRule,
            now = now,
            eventDateTime = eventDateTime
        )
        // A dormant TIME_SINCE_LAST reminder stores `now`: it arms from onEntryLogged, never
        // from this column, and a stale past epoch would only invite a bulk pass to "recover" it.
        val id = reminderRepository.save(reminder.copy(nextTriggerAt = trigger ?: now))
        if (trigger != null && reminder.isActive) {
            reminderScheduler.schedule(reminder.copy(id = id, nextTriggerAt = trigger))
        } else {
            reminderScheduler.cancel(id)
        }
        id
    }

    /**
     * A reminder just rang: advance it to its next occurrence and re-arm, or retire a spent
     * one-shot. TIME_SINCE_LAST is skipped — only a logged entry reschedules it ([onEntryLogged]).
     */
    suspend fun onFired(reminder: Reminder) = rearmMutex.withLock {
        if (reminder.recurrenceRule.type == RecurrenceType.TIME_SINCE_LAST) return@withLock
        val nextTrigger = RecurrenceCalculator.computeNextTrigger(
            rule = reminder.recurrenceRule,
            after = System.currentTimeMillis()
        )
        if (nextTrigger != null) {
            reminderRepository.updateNextTrigger(reminder.id, nextTrigger)
            reminderScheduler.schedule(reminder.copy(nextTriggerAt = nextTrigger))
        } else {
            reminderRepository.setActive(reminder.id, false)
        }
    }

    /**
     * The user tapped Snooze: defer this reminder by its own configured duration.
     *
     * The deferral is **persisted**, because the OS alarm alone does not survive the things
     * that read this row back. Before it was: a reboot mid-snooze re-armed the stored regular
     * occurrence — instantly, for a one-shot whose stored epoch had already elapsed — and any
     * bulk reconcile silently dropped the snooze back to the next regular time.
     *
     * A one-shot is inactive by the time it gets here ([onFired] retires it the instant it
     * rings, before the user can reach the button) and [ReminderScheduler.schedule] refuses
     * inactive reminders, so it is re-activated first; that is a no-op for recurring rules.
     */
    suspend fun snooze(reminderId: Long) = rearmMutex.withLock {
        val reminder = reminderRepository.getById(reminderId) ?: return@withLock
        val snoozeUntil = System.currentTimeMillis() + reminder.snoozeMinutes * 60_000L
        reminderRepository.updateNextTrigger(reminderId, snoozeUntil)
        reminderRepository.setActive(reminderId, true)
        reminderScheduler.schedule(reminder.copy(nextTriggerAt = snoozeUntil, isActive = true))
    }

    /**
     * The Reminders-list switch. Re-enabling goes through [Reminder.reactivated], which keeps a
     * still-future stored trigger and recomputes an elapsed one — arming a past epoch would make
     * the alarm ring the moment the switch flips. Both fields are persisted before arming: the
     * receiver re-reads the row when the alarm fires and stays silent if it still looks inactive.
     */
    suspend fun setActive(reminder: Reminder, isActive: Boolean) = rearmMutex.withLock {
        if (isActive) {
            val armed = reminder.reactivated()
            reminderRepository.updateNextTrigger(armed.id, armed.nextTriggerAt)
            reminderRepository.setActive(armed.id, true)
            reminderScheduler.schedule(armed)
        } else {
            reminderRepository.setActive(reminder.id, false)
            reminderScheduler.cancel(reminder.id)
        }
    }

    /** Cancel the alarm before dropping the row, so no armed alarm outlives its reminder. */
    suspend fun delete(reminderId: Long) = rearmMutex.withLock {
        reminderScheduler.cancel(reminderId)
        reminderRepository.delete(reminderId)
    }

    /**
     * An entry was logged for [eventTypeId]: restart the countdown of every active
     * TIME_SINCE_LAST reminder watching that event, from [entryAt].
     */
    suspend fun onEntryLogged(eventTypeId: Long, entryAt: Long) = rearmMutex.withLock {
        reminderRepository.rescheduleTimeSinceLast(eventTypeId, entryAt) { reminder ->
            reminderScheduler.schedule(reminder)
        }
    }

    // ── Bulk passes ───────────────────────────────────────────────────────────

    /**
     * Reconcile and re-arm every active reminder. Stored triggers go through
     * [RecurrenceCalculator.computeRescheduleTrigger] first, so stale epochs are recomputed
     * from their rules instead of all firing the moment they are armed, and — when
     * [clockChanged] — wall-clock rules are re-anchored to the new local time.
     *
     * A pending snooze rides along in `nextTriggerAt`: still ahead, it is kept and re-armed.
     * The two cases where it is not: one whose time passed while the device was off (recurring
     * rules recompute, so the nudge is skipped rather than fired late) and a wall-clock rule on
     * a [clockChanged] pass, where re-anchoring to the new local time is the point.
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
