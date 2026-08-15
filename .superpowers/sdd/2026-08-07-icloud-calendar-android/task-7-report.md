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
