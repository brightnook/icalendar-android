# Task 7 report: Sync settings and WorkManager scheduling

## Status

Implementation and verification complete.

## TDD evidence

### RED

Command:

```powershell
$env:JAVA_HOME = 'D:\FM\work\jbr'
$env:ANDROID_HOME = 'C:\Users\victo\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'C:\Users\victo\AppData\Local\Temp\ioscalendar-task7-gradle'
.\gradlew.bat testDebugUnitTest --tests "*WorkManagerSyncSchedulerTest" --tests "*CalendarSyncWorkerTest" --no-daemon
```

Result: `:app:compileDebugUnitTestKotlin` failed before production implementation. The expected missing Task 7 symbols were unresolved: `SyncMode`, `CalendarSyncWorker`, `WorkManagerSyncScheduler`, and `SyncDisabledException`; the missing worker consequently also made `doWork` unresolved. This was a valid RED for the requested feature surface.

### GREEN

The same focused command completed with:

```text
BUILD SUCCESSFUL in 24s
32 actionable tasks: 3 executed, 29 up-to-date
```

It ran six tests: three real WorkManager scheduling tests and three direct `CalendarSyncWorker.doWork()` result-mapping tests.

The first GREEN attempt had three Worker-test setup failures, not product failures: `ApplicationProvider` reported that no instrumentation was registered. Root cause was that the Worker test lacked the Robolectric runner while the scheduler test already used it. Adding that runner was the only follow-up change; the rerun above passed.

## Implementation

- Added `AppSettings` and the exact `MANUAL`, `HOURLY`, `DAILY`, `DISABLED` sync modes.
- Added observable `SettingsStore` backed by Preferences DataStore, defaulting malformed or absent stored values to manual mode.
- Added `CalendarSyncWorker`, which submits a refresh to the existing `SyncCoordinator` and maps success/partial success to `Result.success()`, authentication required to `Result.failure()`, and retryable failures to `Result.retry()`.
- Added `SyncScheduler` and `WorkManagerSyncScheduler`: hourly and daily create network-constrained unique periodic work named `icloud-calendar-periodic-sync` with `ExistingPeriodicWorkPolicy.UPDATE`; manual cancels periodic work and permits a network-constrained one-time refresh; disabled cancels periodic work and rejects manual work.
- Wired the settings store and scheduler through `AppContainer`.

## Design decisions

- Work uses a one-hour or 24-hour repeat interval only; no code or documentation claims exact execution timing.
- `CalendarSyncWorker` uses the already-completed coordinator contract unchanged: `SyncRequest.Refresh` and the existing sealed `SyncResult` variants.
- A constructor injection seam lets the Worker tests invoke the real `doWork()` with a result-producing `SyncCoordinator`; the WorkManager production constructor still resolves the coordinator from `CalendarApplication`.
- The scheduler uses WorkManager's `UPDATE` policy so changing hourly to daily replaces the existing account-wide periodic specification rather than retaining an hourly request. The test observes the real WorkManager `WorkInfo` periodicity after this update.
- `enqueueManual()` uses a uniquely named one-time request with replace semantics so the returned UUID represents the queued manual refresh.

## Tests and verification

Focused:

```text
testDebugUnitTest --tests "*WorkManagerSyncSchedulerTest" --tests "*CalendarSyncWorkerTest"
BUILD SUCCESSFUL in 24s; 6/6 passing.
```

Affected suite:

```text
testDebugUnitTest --no-daemon
BUILD SUCCESSFUL in 30s; 133 tests, 0 failures/errors.
```

Fresh required verification:

```text
testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 1m 19s; 90 actionable tasks executed.
```

Fresh verification produced `app-debug.apk`, `app-debug-androidTest.apk`, and the debug lint report. No device was connected, so `androidTest` was assembled only and not claimed as executed on a device. Gradle emitted pre-existing environment warnings about SDK XML version 4, Java native-access future compatibility in Robolectric, and unstrippable packaged native libraries; none were lint or test failures.

`git diff --check` completed with no whitespace errors.

## Files changed

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- `app/src/main/java/com/victor2319/ioscalendar/domain/AppSettings.kt`
- `app/src/main/java/com/victor2319/ioscalendar/data/SettingsStore.kt`
- `app/src/main/java/com/victor2319/ioscalendar/data/DataStoreSettingsStore.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/CalendarSyncWorker.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/SyncScheduler.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/WorkManagerSyncScheduler.kt`
- `app/src/test/java/com/victor2319/ioscalendar/sync/CalendarSyncWorkerTest.kt`
- `app/src/test/java/com/victor2319/ioscalendar/sync/WorkManagerSyncSchedulerTest.kt`

## Self-review

- Confirmed all four modes are covered: hourly/daily scheduling and update, manual cancellation plus one-time work, disabled cancellation plus rejection.
- Confirmed tests observe actual WorkManager records and invoke the Worker implementation directly, rather than asserting mock calls or source text.
- Confirmed `SyncCoordinator`, `SyncRequest`, and `SyncResult` behavior was not changed.
- Confirmed the staged scope excludes the user-owned `gradle.properties` and untracked `gradle/gradle-daemon-jvm.properties`.

## Concerns

None for the Task 7 implementation. WorkManager periodic work is intentionally approximate; it does not provide exact sync timing.

## Fix Round 1

### Review findings verified

- The project already uses `androidx.datastore:datastore-preferences:1.2.1`. Its `preferencesDataStore` overload accepts a `ReplaceFileCorruptionHandler`, so recovery is implemented at the real Preferences DataStore boundary.
- The original scheduler kept an in-memory `MANUAL` default, did not await hourly/daily WorkManager operations, and had no application startup restoration. Those findings were confirmed before changing production code.

### RED

Command:

```powershell
$env:JAVA_HOME = 'D:\FM\work\jbr'
$env:ANDROID_HOME = 'C:\Users\victo\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'C:\Users\victo\AppData\Local\Temp\ioscalendar-task7-gradle'
.\gradlew.bat testDebugUnitTest --tests "*DataStoreSettingsStoreTest" --tests "*WorkManagerSyncSchedulerTest" --tests "*CalendarSyncWorkerTest" --no-daemon
```

Result: `BUILD FAILED in 20s` with 30 actionable tasks. The focused tests did not compile because `DataStoreSettingsStore` accepted only `Context` (not a real injected `DataStore<Preferences>` test boundary) and `WorkManagerSyncScheduler` accepted only `WorkManager` (not durable settings and a coroutine scope). The errors were direct missing Task 7 Fix Round 1 API/behavior seams, so this was a valid RED rather than an environment failure.

### GREEN

The focused command was rerun with `--rerun-tasks` after the minimal implementation:

```text
BUILD SUCCESSFUL in 51s
```

Its XML results recorded 11 tests with zero failures and zero errors: three DataStore settings recovery tests, five real WorkManager scheduler tests, and three direct Worker result-mapping tests.

The first focused GREEN candidate exposed one test timing failure: a recreated scheduler correctly rejected manual work with `SyncSettingsNotReadyException` before its asynchronous initial settings observation arrived. The test was refined to wait for the observable recovered terminal behavior and verifies `SyncDisabledException`; it does not permit manual work during initialization. The succeeding rerun above is the final GREEN evidence.

### Implementation and design

- `DataStoreSettingsStore` now installs `ReplaceFileCorruptionHandler { emptyPreferences() }` and emits `emptyPreferences()` for recoverable `IOException`s. Absent, malformed enum values, corruption, and recoverable reads all resolve to the default manual settings flow.
- The scheduler's durable source of truth is `SettingsStore`, not a default volatile mode. `CalendarApplication.onCreate()` creates the scheduler so an application-scoped observer restores the persisted setting after process recreation.
- `CalendarApplication` supplies WorkManager `Configuration.Provider`, which preserves correct WorkManager initialization while startup restoration is triggered from `onCreate()`.
- An application-scoped coroutine observes stored settings. A `Mutex` serializes that observer and `applyMode`; each WorkManager enqueue/cancel operation is awaited before `applyMode` returns. This prevents an hourly/disabled interleaving from leaving periodic work scheduled.
- `enqueueManual()` remains non-suspending and therefore never blocks the main thread. Before the first settings emission it fails closed with `SyncSettingsNotReadyException`; after restoration, disabled rejects via `SyncDisabledException`, while manual mode continues to allow the one-time refresh.

### Fix Round 1 tests and verification

Focused tests:

```text
testDebugUnitTest --tests "*DataStoreSettingsStoreTest" --tests "*WorkManagerSyncSchedulerTest" --tests "*CalendarSyncWorkerTest" --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 51s; 11 tests, 0 failures/errors.
```

Affected suite:

```text
testDebugUnitTest --no-daemon
BUILD SUCCESSFUL in 24s; 138 tests, 0 failures/errors.
```

Fresh required verification:

```text
testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 1m 12s; 90 actionable tasks executed.
```

The matrix reran unit tests and lint, produced the debug APK and the debug Android-test APK, and did not run `androidTest` on a device. The environment emitted the pre-existing SDK XML, Robolectric native-access, protobuf `Unsafe`, unstrippable-native-library, and version-update warnings; lint had no errors after the WorkManager startup metadata fix.

### Fix Round 1 self-review

- Startup observer lifetime is tied to `Application` through a `SupervisorJob`/`Dispatchers.IO` scope; it is not recreated per UI object or test call.
- Settings persistence happens before direct scheduling, and the mutex covers both settings-observer and caller transitions. Observer replays may repeat the same state but cannot make a later persisted transition final in the wrong order.
- The corruption/read-failure tests exercise real DataStore APIs and flow output. Scheduler tests inspect actual WorkManager records and force a deterministic delayed hourly write followed by disabled cancellation.
- The only intentionally added pre-restoration exception is fail-closed and avoids blocking the main thread; callers cannot enqueue work under an unknown persisted mode.

### Fix Round 1 files changed

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/victor2319/ioscalendar/CalendarApplication.kt`
- `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- `app/src/main/java/com/victor2319/ioscalendar/data/DataStoreSettingsStore.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/SyncScheduler.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/WorkManagerSyncScheduler.kt`
- `app/src/test/java/com/victor2319/ioscalendar/data/DataStoreSettingsStoreTest.kt`
- `app/src/test/java/com/victor2319/ioscalendar/sync/WorkManagerSyncSchedulerTest.kt`
- `.superpowers/sdd/2026-08-07-icloud-calendar-android/task-7-report.md`

## Fix Round 2

### Findings verified and design

- `enqueueManual()` previously read a separate atomic mode and called WorkManager outside the scheduler transition boundary. A concurrent disable could therefore finish after the read but before the manual work submission.
- The prior `SyncSettingsNotReadyException` was only an implementation detail: callers had no public readiness signal and the recreation test spun by retrying the exception.
- The public `SyncScheduler` contract now exposes `StateFlow<SyncSchedulerReadiness>` with `INITIALIZING` and `READY`; `enqueueManual(): UUID` remains non-suspending. `AppContainer.syncSchedulerReadiness` exposes the same flow and `CalendarApplication.onCreate()` initializes it.
- The final linearization mechanism is an atomic scheduler state with active-manual reservations. A manual call atomically reserves the ready non-disabled state before it enqueues. A settings observer serializes WorkManager operations, then suspends until existing manual reservations drain before publishing the new mode. Thus a manual reserved before disabled is allowed, while a manual after disabled is published throws; no UI or observer thread blocks on a Java lock.

### RED

Command:

```powershell
$env:JAVA_HOME = 'D:\FM\work\jbr'
$env:ANDROID_HOME = 'C:\Users\victo\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'C:\Users\victo\AppData\Local\Temp\ioscalendar-task7-gradle'
.\gradlew.bat testDebugUnitTest --tests "*WorkManagerSyncSchedulerTest" --no-daemon
```

Result: `BUILD FAILED in 16s` with 30 actionable tasks. The focused test compiled only after test-local nullability/import corrections, then failed solely because the production readiness contract (`SyncSchedulerReadiness` and `readiness`) and deterministic enqueue boundary (`ManualWorkEnqueuer` and four-argument constructor) did not exist. This was a valid RED for the two Round 2 requirements.

### Debugging note

The first GREEN implementation used a `ReentrantLock` for the short manual check/enqueue section. A controlled test delegate held that lock while the test scheduler's observer attempted to publish disabled, which blocked the scheduler and prevented the test coroutine from releasing the delegate. `jcmd Thread.print` confirmed the lock cycle. The task JVMs were stopped, and the lock implementation was replaced rather than patched: the atomic reservation/drained-deferred design above suspends the transition coroutine instead of blocking a thread.

### GREEN

Focused command:

```text
testDebugUnitTest --tests "*WorkManagerSyncSchedulerTest" --tests "*DataStoreSettingsStoreTest" --tests "*CalendarSyncWorkerTest" --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 42s; 13 tests, 0 failures/errors.
```

The scheduler tests now wait for `readiness.first { READY }`, rather than sleeping, spinning, or catching readiness exceptions. They prove normal manual mode works after readiness, a recreated disabled store rejects after readiness, and a controlled real-WorkManager enqueue cannot let disabled complete between the manual check and its enqueue.

Affected suite:

```text
testDebugUnitTest --no-daemon
BUILD SUCCESSFUL in 19s; 140 tests, 0 failures/errors.
```

Fresh required verification:

```text
testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 59s; 90 actionable tasks executed.
```

The command reran lint and unit tests, assembled the debug APK and debug Android-test APK, and did not run `androidTest` on a device. Environment warnings remained limited to SDK XML, Robolectric native access, protobuf `Unsafe`, WorkManager manifest-merger metadata, unstrippable native libraries, and dependency/version notices; none were lint or test errors.

### Fix Round 2 self-review

- `applyMode` writes settings and waits for the observer's `READY` mode publication; it therefore returns only after persistence and the serialized WorkManager operation complete.
- The observer never blocks a thread while a manual request is active: it awaits `CompletableDeferred` from a coroutine. The only manual synchronization is atomic compare-and-set; the existing immediate WorkManager submission remains non-suspending.
- The blocking test decorator delegates its final action to the real `WorkManager`, and assertions inspect actual `WorkInfo`, so it does not assert mock calls.
- A manual reservation that wins before the disabled publication completes is intentionally allowed; a later call sees `DISABLED` and throws. This is the documented linearization boundary.

### Fix Round 2 files changed

- `app/src/main/java/com/victor2319/ioscalendar/AppContainer.kt`
- `app/src/main/java/com/victor2319/ioscalendar/CalendarApplication.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/SyncScheduler.kt`
- `app/src/main/java/com/victor2319/ioscalendar/sync/WorkManagerSyncScheduler.kt`
- `app/src/test/java/com/victor2319/ioscalendar/sync/WorkManagerSyncSchedulerTest.kt`
- `.superpowers/sdd/2026-08-07-icloud-calendar-android/task-7-report.md`
