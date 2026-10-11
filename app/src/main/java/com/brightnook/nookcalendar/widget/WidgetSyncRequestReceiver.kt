package com.brightnook.nookcalendar.widget

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.domain.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetSyncRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetIntentFactory.ACTION_REFRESH) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as CalendarApplication
                if (application.container.settingsStore.settings.first().syncMode == SyncMode.DISABLED) {
                    context.startActivity(WidgetIntentFactory.settingsIntent(context))
                } else {
                    WidgetSyncJobScheduler.schedule(context)
                }
            } catch (_: Throwable) {
                // Widget refresh is best effort; the app shows sync errors when it is opened.
            } finally {
                pending.finish()
            }
        }
    }
}

internal object WidgetSyncJobScheduler {
    const val JOB_ID = 0x4E4F4F4B

    fun schedule(context: Context): Int {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, WidgetSyncJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        return scheduler.schedule(job)
    }
}
