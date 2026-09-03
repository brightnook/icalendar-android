package com.brightnook.nookcalendar.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.ScheduledReminder
import com.brightnook.nookcalendar.domain.SystemClockProvider
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ReminderScheduler {
    val hasUpcomingReminders: StateFlow<Boolean>
    suspend fun reconcile()
    fun initialize()
}

object NoOpReminderScheduler : ReminderScheduler {
    override val hasUpcomingReminders = MutableStateFlow(false)
    override suspend fun reconcile() = Unit
    override fun initialize() = Unit
}

class AndroidReminderScheduler(
    private val context: Context,
    private val repository: CalendarRepository,
    private val isPremium: () -> Boolean,
) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableHasUpcomingReminders = MutableStateFlow(false)
    override val hasUpcomingReminders: StateFlow<Boolean> = mutableHasUpcomingReminders

    override fun initialize() {
        ReminderNotifier.createChannel(context)
    }

    override suspend fun reconcile() {
        val planned = remindersForEntitlement(
            reminders = repository.upcomingReminders(SystemClockProvider.now),
            premium = isPremium(),
        )
        mutableHasUpcomingReminders.value = planned.isNotEmpty()
        val plannedIds = planned.mapTo(linkedSetOf()) { it.reminderId }
        preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty()
            .filterNot(plannedIds::contains)
            .forEach(::cancel)
        planned.forEach(::schedule)
        preferences.edit().putStringSet(SCHEDULED_IDS, plannedIds).apply()
    }

    private fun schedule(reminder: ScheduledReminder) {
        val payload = Intent(context, ReminderReceiver::class.java).apply {
            data = reminderUri(reminder.reminderId)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.reminderId)
            putExtra(ReminderReceiver.EXTRA_INSTANCE_ID, reminder.instanceId)
            putExtra(ReminderReceiver.EXTRA_DATE, reminder.date.toString())
            putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderReceiver.EXTRA_LOCATION, reminder.location)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(reminder.reminderId),
            payload,
            PENDING_FLAGS,
        )
        val triggerAt = reminder.triggerAt.toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancel(reminderId: String) {
        val pendingIntent = broadcastIntent(reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun broadcastIntent(reminderId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(reminderId),
        Intent(context, ReminderReceiver::class.java).apply { data = reminderUri(reminderId) },
        PENDING_FLAGS,
    )

    private fun reminderUri(reminderId: String): Uri = Uri.parse("nookcalendar://reminder/$reminderId")

    private fun requestCode(reminderId: String): Int =
        MessageDigest.getInstance("SHA-256").digest(reminderId.toByteArray())
            .take(4).fold(0) { value, byte -> value * 31 + byte.toInt() }

    private companion object {
        const val PREFERENCES = "scheduled_reminders"
        const val SCHEDULED_IDS = "scheduled_ids"
        const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
