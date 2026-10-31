package com.brightnook.nookcalendar

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.auth.KeystoreCredentialsStore
import com.brightnook.nookcalendar.auth.LocalAccountManager
import com.brightnook.nookcalendar.caldav.CalDavGateway
import com.brightnook.nookcalendar.caldav.ICloudCalDavGateway
import com.brightnook.nookcalendar.caldav.Ical4jIcsParser
import com.brightnook.nookcalendar.caldav.IcsParser
import com.brightnook.nookcalendar.data.CalendarDatabase
import com.brightnook.nookcalendar.data.DataStoreSettingsStore
import com.brightnook.nookcalendar.data.DataStoreAutomaticSyncStatusStore
import com.brightnook.nookcalendar.data.RoomCalendarRepository
import com.brightnook.nookcalendar.data.DefaultEventEditingRepository
import com.brightnook.nookcalendar.data.QueuedEventEditingRepository
import com.brightnook.nookcalendar.data.EventOutboxProcessor
import com.brightnook.nookcalendar.data.outboxAccountKey
import com.brightnook.nookcalendar.sync.OutboxSyncCoordinator
import com.brightnook.nookcalendar.sync.EventOutboxWorker
import com.brightnook.nookcalendar.data.SettingsStore
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.EventEditingRepository
import com.brightnook.nookcalendar.domain.AccountManager
import com.brightnook.nookcalendar.domain.SystemClockProvider
import com.brightnook.nookcalendar.monetization.BillingGateway
import com.brightnook.nookcalendar.monetization.MonetizationRepository
import com.brightnook.nookcalendar.monetization.PlayBillingGateway
import com.brightnook.nookcalendar.sync.DefaultSyncCoordinator
import com.brightnook.nookcalendar.sync.AndroidSyncDiagnostics
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncScheduler
import com.brightnook.nookcalendar.sync.SyncSchedulerReadiness
import com.brightnook.nookcalendar.sync.WorkManagerSyncScheduler
import com.brightnook.nookcalendar.theme.PrivateThemeAssetStore
import com.brightnook.nookcalendar.theme.ThemeAssetStore
import com.brightnook.nookcalendar.widget.RoomWidgetPreferences
import com.brightnook.nookcalendar.widget.WidgetPreferences
import com.brightnook.nookcalendar.widget.WidgetUpdater
import com.brightnook.nookcalendar.reminder.AndroidReminderScheduler
import com.brightnook.nookcalendar.reminder.ReminderScheduler
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppContainer(val application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: CalendarDatabase by lazy {
        Room.databaseBuilder(application, CalendarDatabase::class.java, "calendar.db")
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .addMigrations(CalendarDatabase.MIGRATION_2_3)
            .addMigrations(CalendarDatabase.MIGRATION_3_4)
            .addMigrations(CalendarDatabase.MIGRATION_4_5)
            .addMigrations(CalendarDatabase.MIGRATION_5_6)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .addMigrations(CalendarDatabase.MIGRATION_8_9)
            .build()
    }

    val calendarRepository: CalendarRepository by lazy { RoomCalendarRepository(database, remindersRequireAccount = true) { credentialsStore.outboxAccountKey() } }
    val reminderScheduler: ReminderScheduler by lazy {
        AndroidReminderScheduler(
            context = application,
            repository = calendarRepository,
            isPremium = { monetizationRepository.state.value.purchaseState == PurchaseUiState.OWNED },
        )
    }
    val widgetPreferences: WidgetPreferences by lazy { RoomWidgetPreferences(database) }
    val widgetUpdater: WidgetUpdater by lazy { WidgetUpdater(application) }
    val settingsStore: SettingsStore by lazy { DataStoreSettingsStore(application) }
    val automaticSyncStatusStore by lazy { DataStoreAutomaticSyncStatusStore(application) }
    val themeAssetStore: ThemeAssetStore by lazy { PrivateThemeAssetStore(application) }
    val credentialsStore: CredentialsStore by lazy { KeystoreCredentialsStore(application) }
    val accountManager: AccountManager by lazy {
        LocalAccountManager(credentialsStore, calendarRepository, syncScheduler, applicationScope, automaticSyncStatusStore,
            onAccountChanged = {
                database.eventOutboxDao().invalidateAccountOverlays()
                refreshLocalEventConsumers()
                val account = credentialsStore.outboxAccountKey()
                if (account != null && database.eventOutboxDao().all().any {
                        it.accountKey == account && it.state in setOf("PENDING", "SYNCING") &&
                            it.phase !in setOf("ACKNOWLEDGED", "REMOTE_DELETED")
                    }) EventOutboxWorker.enqueue(application)
            })
    }
    val calDavGateway: CalDavGateway by lazy { ICloudCalDavGateway() }
    val icsParser: IcsParser by lazy { Ical4jIcsParser() }
    private val billingGateway: BillingGateway by lazy { PlayBillingGateway.create(application) }
    val monetizationRepository: MonetizationRepository by lazy { MonetizationRepository(billingGateway) }
    private val remoteSyncCoordinator: SyncCoordinator by lazy {
        DefaultSyncCoordinator(
            credentialsStore = credentialsStore,
            gateway = calDavGateway,
            parser = icsParser,
            repository = calendarRepository,
            clock = SystemClockProvider,
            diagnostics = AndroidSyncDiagnostics,
            automaticSyncStatusStore = automaticSyncStatusStore,
            reminderScheduler = reminderScheduler,
        )
    }
    private val outboxProcessor by lazy { EventOutboxProcessor(database, credentialsStore, ICloudCalDavGateway(), icsParser) }
    val syncCoordinator: SyncCoordinator by lazy { OutboxSyncCoordinator(remoteSyncCoordinator, outboxProcessor) }
    val eventEditingRepository: EventEditingRepository by lazy {
        val online = DefaultEventEditingRepository(
            credentialsStore = credentialsStore,
            gateway = calDavGateway,
            calendarRepository = calendarRepository,
            syncCoordinator = syncCoordinator,
            widgetRefreshNotifier = widgetUpdater,
        )
        QueuedEventEditingRepository(database, credentialsStore, calendarRepository, online) {
            refreshLocalEventConsumers()
            EventOutboxWorker.enqueue(application)
        }
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

    private fun refreshLocalEventConsumers() {
        applicationScope.launch { widgetUpdater.refreshAll() }
        applicationScope.launch { reminderScheduler.reconcile() }
    }
}
