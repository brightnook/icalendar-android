package com.victor2319.ioscalendar

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.KeystoreCredentialsStore
import com.victor2319.ioscalendar.caldav.CalDavGateway
import com.victor2319.ioscalendar.caldav.ICloudCalDavGateway
import com.victor2319.ioscalendar.caldav.Ical4jIcsParser
import com.victor2319.ioscalendar.caldav.IcsParser
import com.victor2319.ioscalendar.data.CalendarDatabase
import com.victor2319.ioscalendar.data.DataStoreSettingsStore
import com.victor2319.ioscalendar.data.RoomCalendarRepository
import com.victor2319.ioscalendar.data.SettingsStore
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.domain.SystemClockProvider
import com.victor2319.ioscalendar.sync.DefaultSyncCoordinator
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncScheduler
import com.victor2319.ioscalendar.sync.SyncSchedulerReadiness
import com.victor2319.ioscalendar.sync.WorkManagerSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

class AppContainer(val application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: CalendarDatabase by lazy {
        Room.databaseBuilder(application, CalendarDatabase::class.java, "calendar.db")
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .build()
    }

    val calendarRepository: CalendarRepository by lazy { RoomCalendarRepository(database) }
    val settingsStore: SettingsStore by lazy { DataStoreSettingsStore(application) }
    val credentialsStore: CredentialsStore by lazy { KeystoreCredentialsStore(application) }
    val calDavGateway: CalDavGateway by lazy { ICloudCalDavGateway() }
    val icsParser: IcsParser by lazy { Ical4jIcsParser() }
    val syncCoordinator: SyncCoordinator by lazy {
        DefaultSyncCoordinator(
            credentialsStore = credentialsStore,
            gateway = calDavGateway,
            parser = icsParser,
            repository = calendarRepository,
            clock = SystemClockProvider,
        )
    }
    val syncScheduler: SyncScheduler by lazy {
        WorkManagerSyncScheduler(
            workManager = WorkManager.getInstance(application),
            settingsStore = settingsStore,
            scope = applicationScope,
        )
    }
    val syncSchedulerReadiness: StateFlow<SyncSchedulerReadiness>
        get() = syncScheduler.readiness
}
