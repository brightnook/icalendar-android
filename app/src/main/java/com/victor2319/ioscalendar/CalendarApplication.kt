package com.victor2319.ioscalendar

import android.app.Application
import androidx.work.Configuration

class CalendarApplication : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container.syncScheduler
    }
}
