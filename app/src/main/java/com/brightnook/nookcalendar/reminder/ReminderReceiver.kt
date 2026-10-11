package com.brightnook.nookcalendar.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.brightnook.nookcalendar.MainActivity
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTargetDelivery

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID).orEmpty()
        val date = intent.getStringExtra(EXTRA_DATE).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (reminderId.isBlank() || instanceId.isBlank() || date.isBlank() || title.isBlank()) return
        ReminderNotifier.notify(
            context = context,
            reminderId = reminderId,
            instanceId = instanceId,
            date = date,
            title = title,
            location = intent.getStringExtra(EXTRA_LOCATION),
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.brightnook.nookcalendar.extra.REMINDER_ID"
        const val EXTRA_INSTANCE_ID = "com.brightnook.nookcalendar.extra.INSTANCE_ID"
        const val EXTRA_DATE = "com.brightnook.nookcalendar.extra.DATE"
        const val EXTRA_TITLE = "com.brightnook.nookcalendar.extra.TITLE"
        const val EXTRA_LOCATION = "com.brightnook.nookcalendar.extra.LOCATION"
    }
}

object ReminderNotifier {
    private const val CHANNEL_ID = "icloud_event_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.reminder_notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(
        context: Context,
        reminderId: String,
        instanceId: String,
        date: String,
        title: String,
        location: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val contentIntent = Intent(context, MainActivity::class.java)
            .putExtra(CalendarLaunchTargetDelivery.EXTRA_DATE, date)
            .putExtra(CalendarLaunchTargetDelivery.EXTRA_INSTANCE_ID, instanceId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(location ?: context.getString(R.string.reminder_notification_default_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(reminderId.hashCode(), notification)
    }
}
