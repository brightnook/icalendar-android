package com.victor2319.ioscalendar

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.KeystoreCredentialsStore
import com.victor2319.ioscalendar.auth.LocalAccountManager
import com.victor2319.ioscalendar.caldav.CalDavGateway
import com.victor2319.ioscalendar.caldav.ICloudCalDavGateway
import com.victor2319.ioscalendar.caldav.Ical4jIcsParser
import com.victor2319.ioscalendar.caldav.IcsParser
import com.victor2319.ioscalendar.data.CalendarDatabase
import com.victor2319.ioscalendar.data.DataStoreSettingsStore
import com.victor2319.ioscalendar.data.RoomCalendarRepository
import com.victor2319.ioscalendar.data.SettingsStore
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.domain.AccountManager
import com.victor2319.ioscalendar.domain.SystemClockProvider
import com.victor2319.ioscalendar.monetization.BillingGateway
import com.victor2319.ioscalendar.monetization.MonetizationRepository
import com.victor2319.ioscalendar.monetization.PlayBillingGateway
import com.victor2319.ioscalendar.sync.DefaultSyncCoordinator
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncScheduler
import com.victor2319.ioscalendar.sync.SyncSchedulerReadiness
import com.victor2319.ioscalendar.sync.WorkManagerSyncScheduler
import com.victor2319.ioscalendar.theme.PrivateThemeAssetStore
import com.victor2319.ioscalendar.theme.ThemeAssetStore
import com.victor2319.ioscalendar.widget.RoomWidgetPreferences
import com.victor2319.ioscalendar.widget.WidgetPreferences
import com.victor2319.ioscalendar.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

class AppContainer(val application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: CalendarDatabase by lazy {
        Room.databaseBuilder(application, CalendarDatabase::class.java, "calendar.db")
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .addMigrations(CalendarDatabase.MIGRATION_2_3)
            .addMigrations(CalendarDatabase.MIGRATION_3_4)
            .build()
    }

    val calendarRepository: CalendarRepository by lazy { RoomCalendarRepository(database) }
    val widgetPreferences: WidgetPreferences by lazy { RoomWidgetPreferences(database) }
    val widgetUpdater: WidgetUpdater by lazy { WidgetUpdater(application) }
    val settingsStore: SettingsStore by lazy { DataStoreSettingsStore(application) }
    val themeAssetStore: ThemeAssetStore by lazy { PrivateThemeAssetStore(application) }
    val credentialsStore: CredentialsStore by lazy { KeystoreCredentialsStore(application) }
    val accountManager: AccountManager by lazy {
        LocalAccountManager(credentialsStore, calendarRepository, syncScheduler, applicationScope)
    }
    val calDavGateway: CalDavGateway by lazy { ICloudCalDavGateway() }
    val icsParser: IcsParser by lazy { Ical4jIcsParser() }
    private val billingGateway: BillingGateway by lazy { PlayBillingGateway.create(application) }
    val monetizationRepository: MonetizationRepository by lazy { MonetizationRepository(billingGateway) }
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
