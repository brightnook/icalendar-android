package com.brightnook.nookcalendar

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CalendarApplication : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }
    private val mutableTimeZoneIds = MutableStateFlow(ZoneId.systemDefault())
    val timeZoneIds: StateFlow<ZoneId> = mutableTimeZoneIds
    private val timeZoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIMEZONE_CHANGED) refreshTimeZone()
        }
    }

    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            timeZoneReceiver,
            IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        container.syncSchedulerReadiness
    }

    fun refreshTimeZone() {
        mutableTimeZoneIds.value = ZoneId.systemDefault()
    }
}
