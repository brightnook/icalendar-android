# iCloud Calendar for Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local-only Android application that reads iCloud calendars through CalDAV, displays them in a calendar/agenda UI, and exposes independently configurable agenda widgets.

**Architecture:** Use one Android application module organized into focused packages. A CalDAV gateway and iCalendar parser feed a transactional Room repository; Compose UI, WorkManager, and an App Widget read the same local data. Credentials are wrapped by Android Keystore, and no backend or analytics service is introduced.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 9.1.1, Gradle 9.3.1, JDK 17, Android SDK 36, Jetpack Compose BOM 2026.06.00, Material 3, Room 2.8.4, WorkManager 2.11.2, OkHttp 5.3.0, iCal4j 4.2.5, JUnit 5, MockWebServer 5.3.0, Robolectric, and AndroidX Test.

## Global Constraints

- Package name: `com.brightnook.nookcalendar`.
- Working application name: `iOSCalendar`; store-facing naming is deferred to the Google Play release phase.
- `minSdk = 28` (Android 9), `compileSdk = 36`, and `targetSdk = 36`.
- Android 16 is the primary physical-device target; Android 9 and Android 12 are compatibility targets.
- The first release is read-only: no create, edit, delete, or reminder scheduling.
- Synchronization covers the first day of the current month through one year ahead; history is fetched one month at a time on demand.
- Sync modes are manual, approximately hourly, approximately daily, and disabled.
- Chinese and English are the only first-release translations; other locales fall back to English.
- User credentials and calendar data stay on the device and communicate directly with iCloud over HTTPS.
- No backend, advertising, analytics, tracking SDK, Android Calendar Provider, or cloud backup of credentials/calendar data.
- Never commit a real Apple ID, app-specific password, captured iCloud response, or personal calendar fixture.
- Use fake CalDAV XML and synthetic ICS fixtures in automated tests.
- Every task follows red-green-refactor: add a focused failing test, confirm the expected failure, implement the minimum behavior, and rerun the narrow test plus the affected suite.

## File Structure

```text
.
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
└── app
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src
        ├── main
        │   ├── AndroidManifest.xml
        │   ├── java/com/victor2319/ioscalendar
        │   │   ├── CalendarApplication.kt
        │   │   ├── AppContainer.kt
        │   │   ├── MainActivity.kt
        │   │   ├── auth/
        │   │   ├── caldav/
        │   │   ├── data/
        │   │   ├── domain/
        │   │   ├── sync/
        │   │   ├── ui/
        │   │   └── widget/
        │   └── res/
        ├── test/java/com/victor2319/ioscalendar/
        └── androidTest/java/com/victor2319/ioscalendar/
```

`auth` owns secret storage and login validation. `caldav` owns HTTP/XML/iCalendar interoperability. `data` owns Room entities, DAOs, mappings, and repository implementation. `domain` owns UI-independent types and interfaces. `sync` owns orchestration and WorkManager. `ui` owns Compose screens and state holders. `widget` owns widget rendering, configuration, and click intents.

---

### Task 1: Reproducible Android project shell

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/victor2319/ioscalendar/CalendarApplication.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-zh-rCN/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Test: `app/src/test/java/com/victor2319/ioscalendar/BuildSmokeTest.kt`

**Interfaces:**
- Consumes: none.
- Produces: `CalendarApplication.container: AppContainer`, a launchable `MainActivity`, and working Gradle test/build commands.

- [ ] **Step 1: Install the build prerequisites**

Install the current stable Android Studio with JDK 17 and Android SDK Platform 36. Confirm Android Studio's SDK Manager has Platform 36 and Build Tools 36.0.0. Do not store a machine-specific SDK path in Git.

- [ ] **Step 2: Create the failing smoke test**

```kotlin
package com.brightnook.nookcalendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.brightnook.nookcalendar", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 3: Verify the test cannot run before the project exists**

Run: `.\gradlew.bat testDebugUnitTest --tests "*BuildSmokeTest"`

Expected: Gradle fails because the wrapper or Android project is not complete.

- [ ] **Step 4: Scaffold the Gradle project with pinned stable versions**

Use Gradle 9.3.1, AGP 9.1.1, built-in Kotlin, the Kotlin Compose compiler plugin 2.3.21, KSP 2.3.8, Compose BOM 2026.06.00, Room 2.8.4, WorkManager 2.11.2, OkHttp BOM 5.3.0, and iCal4j 4.2.5. Configure Java/Kotlin target 17, Compose, BuildConfig, unit-test Android resources, and JUnit Platform.

The initial manifest must declare `android.permission.INTERNET`, set `android:allowBackup="false"`, set `android:fullBackupContent="false"`, and use `CalendarApplication`. It must not request calendar, contacts, storage, notification, or exact-alarm permissions.

- [ ] **Step 5: Add the minimal application shell**

```kotlin
class CalendarApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(val application: Application)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text(stringResource(R.string.app_name)) } }
    }
}
```

- [ ] **Step 6: Verify the shell**

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug`

Expected: all tasks succeed and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle app
git commit -m "build: scaffold Android calendar app"
```

### Task 2: Domain model and transactional local database

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/Models.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/CalendarRepository.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/Entities.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/CalendarDao.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/EventDao.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/SyncStateDao.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/CalendarDatabase.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/RoomCalendarRepository.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/EntityMappings.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/data/RoomCalendarRepositoryTest.kt`

**Interfaces:**
- Consumes: `Application` from `AppContainer`.
- Produces: `CalendarRepository`, `CalendarSummary`, `CalendarEvent`, `EventInstance`, `EventDetail`, `TimeRange`, and `SyncCoverage`.

```kotlin
interface CalendarRepository {
    fun observeCalendars(): Flow<List<CalendarSummary>>
    fun observeAgenda(range: TimeRange, calendarIds: Set<String>): Flow<List<EventInstance>>
    fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>>
    fun observeEvent(instanceId: String): Flow<EventDetail?>
    suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot)
    suspend fun applyCalendarDelta(delta: CalendarDelta)
    suspend fun setCalendarVisible(calendarId: String, visible: Boolean)
    suspend fun recordCoverage(calendarId: String, range: TimeRange)
    suspend fun clearCalendarCache()
}
```

- [ ] **Step 1: Write repository tests for ordering, visibility, and rollback**

Create an in-memory Room database test that inserts an all-day event and two timed events, verifies all-day-first ordering, hides one calendar, and verifies `replaceCalendarSnapshot()` rolls back if an event violates a foreign key.

```kotlin
@Test
fun agendaOrdersAllDayBeforeTimedEvents() = runTest {
    repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())
    val items = repository.observeAgenda(dayRange, setOf("work")).first()
    assertEquals(listOf("all-day", "09:00", "14:00"), items.map { it.title })
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*RoomCalendarRepositoryTest"`

Expected: compilation fails because domain types and repository do not exist.

- [ ] **Step 3: Implement focused Room entities and DAOs**

Use normalized tables for calendars, event masters, expanded instances, sync state, coverage, and widget configuration. Store timestamps as epoch milliseconds and date-only values as ISO local dates. Add a unique index on `(calendarId, uid, recurrenceId)` and foreign keys with cascade deletion.

- [ ] **Step 4: Implement repository transactions and mappings**

`replaceCalendarSnapshot()` must replace one calendar's covered-range data in one `withTransaction` block. `applyCalendarDelta()` must upsert changed masters/instances and delete server-deleted UIDs in one transaction.

- [ ] **Step 5: Run database tests and schema export**

Run: `.\gradlew.bat testDebugUnitTest --tests "*RoomCalendarRepositoryTest"`

Expected: all repository tests pass and Room exports schema version 1 to `app/schemas/`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/domain app/src/main/java/com/victor2319/ioscalendar/data app/src/test/java/com/victor2319/ioscalendar/data app/schemas app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt
git commit -m "feat: add local calendar repository"
```

### Task 3: Keystore-backed credential storage

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/auth/Credentials.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/auth/CredentialsStore.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/auth/KeystoreCredentialsStore.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/auth/SecretCipher.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/auth/KeystoreCredentialsStoreTest.kt`
- Test: `app/src/androidTest/java/com/victor2319/ioscalendar/auth/KeystoreCredentialsStoreInstrumentedTest.kt`

**Interfaces:**
- Consumes: Android application context.
- Produces: `CredentialsStore.save(Credentials)`, `CredentialsStore.load(): Credentials?`, and `CredentialsStore.clear()`.

```kotlin
data class Credentials(val appleId: String, val appSpecificPassword: CharArray)

interface CredentialsStore {
    suspend fun save(credentials: Credentials)
    suspend fun load(): Credentials?
    suspend fun clear()
}
```

- [ ] **Step 1: Write tests for round-trip, clear, and corrupt ciphertext**

The JVM test uses a fake `SecretCipher`; the instrumented test uses Android Keystore. Assert persisted preferences do not contain the plaintext Apple ID or password.

- [ ] **Step 2: Run tests and confirm missing implementation**

Run: `.\gradlew.bat testDebugUnitTest --tests "*KeystoreCredentialsStoreTest"`

Expected: compilation fails because `CredentialsStore` does not exist.

- [ ] **Step 3: Implement AES-GCM wrapping with an Android Keystore key**

Create a non-exportable AES-256 key named `ioscalendar.credentials.v1`, generate a fresh 12-byte IV per save, authenticate ciphertext with GCM, and store only versioned base64 ciphertext plus IV in private preferences. Convert password text to `CharArray` at the boundary and clear temporary byte/character arrays in `finally` blocks.

- [ ] **Step 4: Verify both test layers**

Run: `.\gradlew.bat testDebugUnitTest --tests "*KeystoreCredentialsStoreTest" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.brightnook.nookcalendar.auth.KeystoreCredentialsStoreInstrumentedTest`

Expected: fake and device round-trip tests pass; plaintext assertions pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/auth app/src/test/java/com/victor2319/ioscalendar/auth app/src/androidTest/java/com/victor2319/ioscalendar/auth app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt
git commit -m "feat: secure iCloud credentials locally"
```

### Task 4: iCalendar parsing and bounded recurrence expansion

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/IcsParser.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/Ical4jIcsParser.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/ParsedEvent.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/RecurrenceExpander.kt`
- Create: `app/src/test/resources/ics/all-day.ics`
- Create: `app/src/test/resources/ics/dst-recurring-with-exception.ics`
- Create: `app/src/test/resources/ics/cancelled-instance.ics`
- Test: `app/src/test/java/com/victor2319/ioscalendar/caldav/Ical4jIcsParserTest.kt`

**Interfaces:**
- Consumes: synthetic ICS text and a bounded `TimeRange`.
- Produces: `IcsParser.parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar`.

```kotlin
interface IcsParser {
    fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar
}

data class ParsedCalendar(
    val events: List<ParsedEvent>,
    val instances: List<ParsedInstance>,
)
```

- [ ] **Step 1: Add synthetic ICS fixtures and failing assertions**

Assert exclusive `DTEND` handling for all-day events, stable local time across a DST transition, replacement of one recurrence by `RECURRENCE-ID`, and removal of a `STATUS:CANCELLED` instance.

- [ ] **Step 2: Run parser tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*Ical4jIcsParserTest"`

Expected: compilation fails because `IcsParser` is absent.

- [ ] **Step 3: Implement parsing through iCal4j**

Parse `VEVENT`, preserve UID and original TZID, normalize all-day values to date boundaries, treat `DTEND` as exclusive, retain master recurrence rules, and expand only inside the requested range. Never pre-expand an unbounded recurrence.

- [ ] **Step 4: Run parser tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "*Ical4jIcsParserTest"`

Expected: all four fixture groups pass in `Asia/Tokyo` and `America/Los_Angeles` display zones.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/caldav app/src/test/java/com/victor2319/ioscalendar/caldav app/src/test/resources/ics
git commit -m "feat: parse and expand iCalendar events"
```

### Task 5: Read-only iCloud CalDAV gateway

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/CalDavGateway.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/ICloudCalDavGateway.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/CalDavXml.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/CalDavModels.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/CalDavFailure.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/caldav/SensitiveLoggingInterceptor.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/caldav/ICloudCalDavGatewayTest.kt`

**Interfaces:**
- Consumes: `Credentials`, OkHttp, fake or real CalDAV endpoints.
- Produces: authentication, calendar discovery, bounded multiget/query, and sync-collection operations.

```kotlin
interface CalDavGateway {
    suspend fun authenticate(credentials: Credentials): Principal
    suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar>
    suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot
    suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta
}
```

- [ ] **Step 1: Write MockWebServer protocol tests**

Queue responses for principal discovery, calendar-home discovery, calendar listing, calendar-query, sync-collection, 401, 403, 429, and malformed XML. Assert request methods and bodies contain `PROPFIND`/`REPORT`, correct depth headers, bounded time ranges, and no credentials in logs.

- [ ] **Step 2: Run gateway tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ICloudCalDavGatewayTest"`

Expected: compilation fails because gateway types are missing.

- [ ] **Step 3: Implement the minimal read-only protocol client**

Use OkHttp with Basic authentication only over HTTPS in production. Follow the server-provided principal and calendar-home URLs instead of hard-coding account-specific paths. Parse WebDAV multistatus XML with `XmlPullParser`. Implement only read operations: `PROPFIND` and `REPORT`; do not expose PUT, POST, PATCH, or DELETE helpers.

- [ ] **Step 4: Classify errors without leaking server bodies**

Map 401/403 to `AuthenticationRejected`, 408/429/5xx to `Temporary`, invalid XML/ICS to `InvalidServerData`, and offline I/O to `NetworkUnavailable`. Keep response snippets out of release logs.

- [ ] **Step 5: Run protocol and parser suites**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ICloudCalDavGatewayTest" --tests "*Ical4jIcsParserTest"`

Expected: all tests pass and the sensitive logging assertion finds no Apple ID or password.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/caldav app/src/test/java/com/victor2319/ioscalendar/caldav app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt
git commit -m "feat: add read-only iCloud CalDAV gateway"
```

### Task 6: Initial, incremental, and on-demand synchronization

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/SyncCoordinator.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/DefaultSyncCoordinator.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/SyncModels.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/SyncMutex.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/ClockProvider.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/sync/DefaultSyncCoordinatorTest.kt`

**Interfaces:**
- Consumes: `CredentialsStore`, `CalDavGateway`, `IcsParser`, `CalendarRepository`, `ClockProvider`.
- Produces: `SyncCoordinator.sync(SyncRequest): SyncResult`.

```kotlin
sealed interface SyncRequest {
    data object Initial : SyncRequest
    data object Refresh : SyncRequest
    data class EnsureMonth(val month: YearMonth) : SyncRequest
}

interface SyncCoordinator {
    suspend fun sync(request: SyncRequest): SyncResult
}
```

- [ ] **Step 1: Write orchestration tests**

Assert `Initial` requests the current month start through one year ahead, `Refresh` prefers a saved sync token, `EnsureMonth` fetches exactly one uncovered month, two concurrent calls share one mutex, and one failed calendar does not discard another calendar's success.

- [ ] **Step 2: Run the tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorTest"`

Expected: compilation fails because synchronization interfaces are absent.

- [ ] **Step 3: Implement synchronization orchestration**

Authenticate once per run, discover remote calendars, map remote IDs stably, fetch each calendar independently, parse inside the requested range, and commit through repository transactions. Persist sync tokens only after their matching database transaction succeeds.

- [ ] **Step 4: Implement partial failure and auth-stop behavior**

Return `SyncResult.Success`, `PartialSuccess`, `AuthenticationRequired`, or `RetryableFailure`. Do not retry authentication failures inside the coordinator. Include counts and calendar IDs in results, never titles or notes.

- [ ] **Step 5: Run affected suites**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorTest" --tests "*RoomCalendarRepositoryTest" --tests "*ICloudCalDavGatewayTest"`

Expected: all suites pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/sync app/src/main/java/com/victor2319/ioscalendar/domain app/src/test/java/com/victor2319/ioscalendar/sync app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt
git commit -m "feat: orchestrate local calendar synchronization"
```

### Task 7: Sync settings and WorkManager scheduling

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/AppSettings.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/SettingsStore.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/data/DataStoreSettingsStore.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/CalendarSyncWorker.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/SyncScheduler.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/sync/WorkManagerSyncScheduler.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/sync/WorkManagerSyncSchedulerTest.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/sync/CalendarSyncWorkerTest.kt`

**Interfaces:**
- Consumes: `SyncCoordinator`, `SettingsStore`, WorkManager.
- Produces: account-wide scheduling and observable sync state.

```kotlin
enum class SyncMode { MANUAL, HOURLY, DAILY, DISABLED }

interface SyncScheduler {
    suspend fun applyMode(mode: SyncMode)
    fun enqueueManual(): UUID
    fun cancelAll()
}
```

- [ ] **Step 1: Write scheduler tests for all four modes**

Assert hourly and daily use unique periodic work named `icloud-calendar-periodic-sync`, require connected network, and use `ExistingPeriodicWorkPolicy.UPDATE`. Assert manual mode cancels periodic work but allows one-time work. Assert disabled mode cancels periodic work and `enqueueManual()` is rejected with `SyncDisabledException`.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WorkManagerSyncSchedulerTest" --tests "*CalendarSyncWorkerTest"`

Expected: compilation fails because scheduler types are missing.

- [ ] **Step 3: Implement settings and scheduling**

Store `SyncMode` in Preferences DataStore. Use one-hour and 24-hour repeat intervals without claiming exact execution times. Map coordinator retryable failures to `Result.retry()`, authentication failures to `Result.failure()`, and success/partial success to `Result.success()`.

- [ ] **Step 4: Verify scheduling semantics**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WorkManagerSyncSchedulerTest" --tests "*CalendarSyncWorkerTest"`

Expected: all four mode tests and worker result mappings pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/domain/AppSettings.kt app/src/main/java/com/victor2319/ioscalendar/data app/src/main/java/com/victor2319/ioscalendar/sync app/src/test/java/com/victor2319/ioscalendar/sync app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt
git commit -m "feat: schedule configurable background sync"
```

### Task 8: Login and first-sync user flow

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/auth/LoginCoordinator.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/login/LoginViewModel.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/login/LoginScreen.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/login/LoginUiState.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/victor2319/ioscalendar/ui/login/LoginViewModelTest.kt`
- Test: `app/src/androidTest/java/com/victor2319/ioscalendar/ui/login/LoginScreenTest.kt`

**Interfaces:**
- Consumes: `CalDavGateway.authenticate`, `CredentialsStore`, `SyncCoordinator.sync(Initial)`.
- Produces: authenticated navigation state and first-sync progress.

```kotlin
interface LoginCoordinator {
    suspend fun connect(appleId: String, appPassword: CharArray): LoginResult
}
```

- [ ] **Step 1: Write login state-machine tests**

Assert empty fields are rejected locally, credentials are saved only after server validation, failed validation clears the password field, successful validation triggers initial sync, and authentication/network/server failures map to different user messages.

- [ ] **Step 2: Run login tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*LoginViewModelTest"`

Expected: compilation fails because login state types are absent.

- [ ] **Step 3: Implement the two-step login screen**

Add a button that opens `https://account.apple.com/` in a Chrome Custom Tab or system browser, a secondary link to `https://support.apple.com/zh-cn/102654`, Apple ID and app-specific-password fields, password visibility control, and a “Connect and sync” action. Do not embed Apple login in a WebView.

- [ ] **Step 4: Implement safe validation and first sync**

Trim surrounding whitespace from the Apple ID and password, preserve the password's internal hyphens exactly as Apple generated them, clear temporary character arrays, validate before storing, and expose progress for discovery and first synchronization.

- [ ] **Step 5: Verify JVM and Compose UI tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "*LoginViewModelTest" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.brightnook.nookcalendar.ui.login.LoginScreenTest`

Expected: validation, browser intent, accessibility labels, failure copy, and successful navigation pass in Chinese and English resources.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/auth app/src/main/java/com/victor2319/ioscalendar/ui app/src/main/java/com/victor2319/ioscalendar/MainActivity.kt app/src/main/res app/src/test/java/com/victor2319/ioscalendar/ui app/src/androidTest/java/com/victor2319/ioscalendar/ui
git commit -m "feat: add secure iCloud connection flow"
```

### Task 9: Calendar, agenda, and modal event details

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/MainCalendarViewModel.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/MainCalendarState.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/MainCalendarScreen.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/MonthGrid.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/AgendaList.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/EventDetailSheet.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/main/CalendarLaunchTarget.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/victor2319/ioscalendar/ui/main/MainCalendarViewModelTest.kt`
- Test: `app/src/androidTest/java/com/victor2319/ioscalendar/ui/main/MainCalendarScreenTest.kt`

**Interfaces:**
- Consumes: `CalendarRepository`, `SyncScheduler`, and optional `CalendarLaunchTarget(date, instanceId)`.
- Produces: fixed month-grid/agenda layout and a shared modal detail interaction.

- [ ] **Step 1: Write state tests**

Assert selecting a day changes the agenda query, changing month calls `EnsureMonth` only when coverage is missing, manual refresh uses the scheduler, and an incoming launch target selects its date and opens its event.

- [ ] **Step 2: Run state tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*MainCalendarViewModelTest"`

Expected: compilation fails because main-screen state is absent.

- [ ] **Step 3: Implement the fixed main layout**

Keep the month grid above the selected-day agenda. Follow system locale for first day of week, date formatting, and 12/24-hour formatting. Place all-day items before timed items, show calendar color, title, time, and location when available.

- [ ] **Step 4: Implement the shared modal bottom sheet**

Display title, calendar, start/end, all-day state, location, notes, and human-readable recurrence. Detect web, telephone, and geo actions through explicit safe intents. Closing the sheet preserves selected date and list position.

- [ ] **Step 5: Verify state and UI**

Run: `.\gradlew.bat testDebugUnitTest --tests "*MainCalendarViewModelTest" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.brightnook.nookcalendar.ui.main.MainCalendarScreenTest`

Expected: month/day selection, ordering, sheet open/close, large font, dark mode, and incoming launch target tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/ui/main app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt app/src/main/java/com/victor2319/ioscalendar/MainActivity.kt app/src/main/res app/src/test/java/com/victor2319/ioscalendar/ui/main app/src/androidTest/java/com/victor2319/ioscalendar/ui/main
git commit -m "feat: show calendar agenda and event details"
```

### Task 10: Settings, calendar visibility, cache, and logout

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/AccountManager.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/auth/LocalAccountManager.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `CalendarRepository`, `SettingsStore`, `SyncScheduler`, `CredentialsStore`.
- Produces: sync-mode settings, global visibility, cache clearing, reconnect, and logout behavior.

- [ ] **Step 1: Write settings tests**

Assert sync-mode changes call `applyMode`, hiding a calendar updates repository visibility, clearing history preserves current/future data, logout always cancels work and clears credentials, and “delete cache” additionally clears calendar rows.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SettingsViewModelTest"`

Expected: compilation fails because settings UI and account manager are absent.

- [ ] **Step 3: Implement settings and confirmation dialogs**

Show manual/hourly/daily/disabled choices with “approximately” copy for periodic modes. Show calendars with original colors and visibility switches. Mask the Apple ID in account status. Require confirmation for cache deletion and logout.

- [ ] **Step 4: Implement deterministic logout order**

Call `SyncScheduler.cancelAll()`, clear credentials, clear authentication state, then conditionally clear cache. Navigate to login even if cache deletion fails, while showing a non-sensitive cleanup warning.

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SettingsViewModelTest" --tests "*WorkManagerSyncSchedulerTest"`

Expected: settings and scheduler tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/ui/settings app/src/main/java/com/victor2319/ioscalendar/domain/AccountManager.kt app/src/main/java/com/victor2319/ioscalendar/auth/LocalAccountManager.kt app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt app/src/main/res app/src/test/java/com/victor2319/ioscalendar/ui/settings
git commit -m "feat: add calendar and account settings"
```

### Task 11: Independently configurable agenda widgets

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/AgendaWidgetProvider.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/AgendaWidgetService.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/AgendaWidgetFactory.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetConfigActivity.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetConfigViewModel.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetPreferences.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/RoomWidgetPreferences.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetIntentFactory.kt`
- Create: `app/src/main/res/layout/agenda_widget.xml`
- Create: `app/src/main/res/layout/agenda_widget_item.xml`
- Create: `app/src/main/res/layout/agenda_widget_empty.xml`
- Create: `app/src/main/res/xml/agenda_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/victor2319/ioscalendar/widget/WidgetConfigViewModelTest.kt`
- Test: `app/src/test/java/com/victor2319/ioscalendar/widget/AgendaWidgetFactoryTest.kt`
- Test: `app/src/androidTest/java/com/victor2319/ioscalendar/widget/WidgetLaunchTest.kt`

**Interfaces:**
- Consumes: local repository queries, `SyncScheduler`, and `CalendarLaunchTarget`.
- Produces: per-widget calendar selection, range selection, rendering, refresh, and event launch.

```kotlin
enum class WidgetRange(val days: Int) { TODAY(1), THREE_DAYS(3), SEVEN_DAYS(7), THIRTY_DAYS(30) }

data class WidgetConfig(
    val appWidgetId: Int,
    val calendarIds: Set<String>,
    val range: WidgetRange,
)
```

- [ ] **Step 1: Write configuration and rendering tests**

Assert a new widget defaults to all globally visible calendars and seven days, two widget IDs retain different filters, hidden global calendars disappear, all-day items sort first, and each row creates an intent containing the correct date and instance ID.

- [ ] **Step 2: Run widget tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WidgetConfigViewModelTest" --tests "*AgendaWidgetFactoryTest"`

Expected: compilation fails because widget classes are absent.

- [ ] **Step 3: Implement a RemoteViews collection widget**

Use `ListView` backed by `RemoteViewsService` for Android 9 compatibility. Group rows by date, show calendar color/title/time, add location only in wider layouts, follow day/night resources, adapt content to resized widget dimensions, and preserve old rows while synchronization runs. Register the provider/service with `BIND_REMOTEVIEWS` protection for the service.

- [ ] **Step 4: Implement configuration, refresh, and deep-link behavior**

Persist config by `appWidgetId`. A row click opens `MainActivity` with `CalendarLaunchTarget`; title opens the current day; refresh enqueues manual sync unless mode is disabled, in which case it opens settings. Delete config in `onDeleted()`.

- [ ] **Step 5: Refresh all widgets after repository changes**

Add a `WidgetUpdater` invoked after successful or partial sync and visibility changes. Call `notifyAppWidgetViewDataChanged()` for each active widget ID.

- [ ] **Step 6: Verify JVM and device behavior**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WidgetConfigViewModelTest" --tests "*AgendaWidgetFactoryTest" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.brightnook.nookcalendar.widget.WidgetLaunchTest`

Expected: independent configuration, rendering order, refresh routing, and event launch pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/widget app/src/main/res/layout app/src/main/res/xml app/src/main/AndroidManifest.xml app/src/main/res/values app/src/main/res/values-zh-rCN app/src/test/java/com/victor2319/ioscalendar/widget app/src/androidTest/java/com/victor2319/ioscalendar/widget
git commit -m "feat: add configurable agenda widgets"
```

### Task 12: Privacy hardening, localization audit, and release acceptance

**Files:**
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `app/src/main/res/xml/backup_rules.xml`
- Create: `app/src/test/java/com/victor2319/ioscalendar/security/PrivacyConfigurationTest.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/security/ManifestPermissionTest.kt`
- Create: `app/src/androidTest/java/com/victor2319/ioscalendar/AcceptanceTest.kt`
- Create: `docs/testing/real-icloud-smoke-test.md`
- Create: `docs/privacy/data-flow.md`
- Create: `THIRD_PARTY_NOTICES.md`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/proguard-rules.pro`
- Modify: `.gitignore`
- Create: `README.md`

**Interfaces:**
- Consumes: the complete application.
- Produces: auditable privacy rules, dependency notices, repeatable release verification, and personal-device installation instructions.

- [ ] **Step 1: Write manifest and privacy guard tests**

Parse the merged manifest and assert only Internet plus framework-required permissions are present, backup is disabled, cleartext traffic is disabled, widget components have explicit exported values, and no analytics service/provider class is registered.

- [ ] **Step 2: Run tests and confirm missing hardening files**

Run: `.\gradlew.bat testDebugUnitTest --tests "*PrivacyConfigurationTest" --tests "*ManifestPermissionTest"`

Expected: tests fail because backup/data-extraction rules and final manifest attributes are missing.

- [ ] **Step 3: Add privacy and release configuration**

Set `usesCleartextTraffic="false"`, reference explicit backup/data-extraction exclusion rules, keep release logging at warning/error with sensitive bodies disabled, enable minification/resource shrinking, and add keep rules required by Room and iCal4j only after verifying the release build.

- [ ] **Step 4: Audit strings and accessibility**

Compare keys in `values/strings.xml` and `values-zh-rCN/strings.xml`; fail the build for missing translations. Run Compose accessibility checks for content descriptions, minimum touch targets, large font, and dark-theme contrast. Confirm other locales display English.

- [ ] **Step 5: Add synthetic end-to-end acceptance coverage**

Use a fake CalDAV gateway and in-memory database to test: connect, discover multiple calendars, initial range, hide calendar, manual refresh, month-on-demand, offline cache, auth expiry, event detail, two widget configs, and logout. The acceptance fixture contains only invented names and dates.

- [ ] **Step 6: Document real-account smoke testing**

In `docs/testing/real-icloud-smoke-test.md`, require a disposable app-specific password, Android 16 physical device, current-month and recurring test events, credential revocation test, airplane-mode cache test, hourly scheduling observation, and immediate password revocation after testing. Explicitly prohibit screenshots containing private events.

- [ ] **Step 7: Run the full verification matrix**

Run:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

Expected: all JVM tests, lint, debug/release builds, and connected tests pass. Inspect the release APK/AAB merged manifest to confirm no unexpected permissions and inspect logs during a fake sync to confirm no credentials or event content.

- [ ] **Step 8: Install and perform the real iCloud smoke test**

Run: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

Follow the documented checklist on Android 16. Record only pass/fail and non-sensitive error categories. Revoke the disposable app-specific password when finished.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest docs/testing docs/privacy THIRD_PARTY_NOTICES.md .gitignore README.md app/proguard-rules.pro
git commit -m "test: harden privacy and verify release scope"
```

## Final Delivery Gate

- [ ] Run `git status --short` and confirm the worktree is clean.
- [ ] Run `.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleRelease` from a clean checkout.
- [ ] Run connected tests on Android 16 and at least one Android 9 or Android 12 emulator.
- [ ] Verify every acceptance criterion in `docs/superpowers/specs/2026-08-07-icloud-calendar-android-design.md` maps to a passing automated or documented manual test.
- [ ] Review dependency licenses: OkHttp Apache-2.0, iCal4j BSD-3-Clause, AndroidX Apache-2.0, and any transitive notices generated by the build.
- [ ] Confirm no real credentials, calendar exports, captured responses, or private screenshots exist in Git history.
- [ ] Only after the user approves the verified build, configure the GitHub remote, push the branch, and open a draft pull request or merge through the user-selected workflow.
