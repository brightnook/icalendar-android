package com.brightnook.nookcalendar.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brightnook.nookcalendar.CalendarApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as CalendarApplication).container.reminderScheduler.reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
