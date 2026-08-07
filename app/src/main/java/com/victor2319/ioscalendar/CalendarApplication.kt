package com.victor2319.ioscalendar

import android.app.Application

class CalendarApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
