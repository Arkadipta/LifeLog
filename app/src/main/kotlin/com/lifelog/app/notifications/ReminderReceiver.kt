package com.lifelog.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.RepeatType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                rescheduleAll(context)
                return
            }
            ACTION_REMINDER -> {
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "LifeLog Reminder"
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                if (reminderId == -1L) return

                NotificationHelper.showReminderNotification(
                    context = context,
                    notificationId = reminderId.toInt(),
                    title = title,
                    message = message,
                    reminderId = reminderId
                )

                CoroutineScope(Dispatchers.IO).launch {
                    val reminder = reminderRepository.getById(reminderId) ?: return@launch
                    if (!reminder.isActive) return@launch

                    val nextTrigger = computeNextTrigger(reminder)
                    if (nextTrigger != null) {
                        reminderRepository.updateNextTrigger(reminderId, nextTrigger)
                        reminderScheduler.schedule(reminder.copy(nextTriggerAt = nextTrigger))
                    } else {
                        reminderRepository.setActive(reminderId, false)
                    }
                }
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val reminders = reminderRepository.getAllActive()
            reminderScheduler.rescheduleAll(reminders)
        }
    }

    private fun computeNextTrigger(reminder: com.lifelog.app.domain.model.Reminder): Long? {
        return when (reminder.repeatType) {
            RepeatType.NONE -> null
            RepeatType.INTERVAL -> System.currentTimeMillis() + reminder.repeatIntervalMinutes * 60_000L
            RepeatType.DAILY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, reminder.timeOfDayMinutes / 60)
                    set(Calendar.MINUTE, reminder.timeOfDayMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            RepeatType.WEEKLY -> {
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
                val futureDays = reminder.daysOfWeek.map { d ->
                    val diff = (d - today + 7) % 7
                    if (diff == 0) 7 else diff
                }
                val daysUntilNext = futureDays.minOrNull() ?: 7
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, reminder.timeOfDayMinutes / 60)
                    set(Calendar.MINUTE, reminder.timeOfDayMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, daysUntilNext)
                }
                cal.timeInMillis
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.lifelog.app.ACTION_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"

        fun buildIntent(context: Context, reminder: com.lifelog.app.domain.model.Reminder): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_MESSAGE, reminder.message)
            }
    }
}
