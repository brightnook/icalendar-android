package com.victor2319.ioscalendar

import android.app.Application
import androidx.room.Room
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.KeystoreCredentialsStore
import com.victor2319.ioscalendar.data.CalendarDatabase
import com.victor2319.ioscalendar.data.RoomCalendarRepository
import com.victor2319.ioscalendar.domain.CalendarRepository

class AppContainer(val application: Application) {
    private val database: CalendarDatabase by lazy {
        Room.databaseBuilder(application, CalendarDatabase::class.java, "calendar.db").build()
    }

    val calendarRepository: CalendarRepository by lazy { RoomCalendarRepository(database) }
    val credentialsStore: CredentialsStore by lazy { KeystoreCredentialsStore(application) }
}
