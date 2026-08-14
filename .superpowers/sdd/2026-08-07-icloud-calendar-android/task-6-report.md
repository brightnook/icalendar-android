# Task 6 Report: Local Calendar Synchronization

## Status

Implemented initial, incremental, and on-demand synchronization on branch
`codex/icloud-calendar-android-dev` from base
`06a742b9de9854ee36047cdf107c934112b08eff`.

Commit: this report is part of `feat: orchestrate local calendar synchronization`.

## RED evidence

- The first valid coordinator RED was `compileDebugUnitTestKotlin FAILED`. The compiler reported the expected absent contracts: `SyncRequest`, `SyncResult`, `DefaultSyncCoordinator`, `ClockProvider`, `CalendarRepository.getSyncState`, `CalendarEvent.remoteResourceHref`, and `CalendarDelta.deletedResourceHrefs`. Earlier attempts stopped before compilation because the shell had no usable JDK/Gradle cache access; those environment failures were not counted as RED.
- The nullable-or-nonblank resource href test failed with `AssertionError` before the `CalendarEvent` invariant was added.
- The resource tombstone ordering test failed with `AssertionError` because the transaction deleted a freshly upserted replacement. Moving resource deletion before upserts made the replacement and rollback tests pass.
- The Room migration helper was rejected before running migration SQL because Room 2.8's SupportSQLite driver compared a configured database name with Robolectric's absolute sandbox path. The final integration test instead creates a real v1 SQLite database from the exported v1 schema, opens it through the real Room builder with `MIGRATION_1_2`, and verifies migrated data and structure.

## GREEN and contract evidence

### Ranges

- `ClockProvider` supplies both `Instant` and `ZoneId`.
- Initial uses local calendar boundaries from current month day 1 at 00:00 through `currentMonth.plusMonths(13)` day 1 at 00:00. The Tokyo test proves the DST-safe instant conversion and identical parser display zone/range.
- Gateway requests are split at 400 days. The required 13-calendar-month window is at most 397 days, so its exact boundaries remain one legal request; the test asserts every call is at most 400 days.
- EnsureMonth uses exactly `[month start, next month start)` and skips only a full coverage range with the same zone.

### Tokens and transactions

- Refresh reads the committed local nonblank token through `CalendarRepository.getSyncState`; discovery-only or blank tokens trigger a bounded baseline snapshot.
- Snapshot and delta tokens are passed only inside repository transaction payloads. Repository failure does not publish the token.
- Resource tombstones carry canonical resource hrefs instead of guessing UIDs from filenames. Room deletes every master for `(calendarId, remoteResourceHref)` first, FK-cascades instances, then applies upserts/other tombstones/token in the same transaction.
- Database v2 adds nullable `remoteResourceHref` plus the non-unique `(calendarId, remoteResourceHref)` index. `MIGRATION_1_2` is registered in `AppContainer`; schema `2.json` is exported. The migration test proves v1 event preservation, null legacy href, the new column, and the new index.

### Orchestration and failures

- Each request authenticates once and discovers once; each calendar performs an independent fetch/parse/repository transaction.
- Stable IDs are SHA-256 values over an account/session-independent namespace and canonical remote calendar href. Tests prove stability across remote IDs, names, and session IDs and non-collision across hrefs.
- `SyncMutex` is one process-wide coroutine `Mutex`. Tests prove maximum concurrent gateway operations is one, both callers finish, and cancelling a waiter does not block the next request.
- Missing credentials and authentication rejection return `AuthenticationRequired`; mid-run rejection stops later calendars while retaining prior success counts. Cancellation is rethrown.
- Temporary/network failures return `RetryableFailure` when no calendar succeeds and `PartialSuccess` after partial work. Invalid data/parser/repository failures are keyed by stable calendar ID and do not stop sibling calendars.
- Results contain only counts, stable IDs, and failure categories. A parser failure fixture containing a secret title proves `toString()` does not expose ICS content or calendar display names.
- Calendar metadata maps to local summary information, and Room preserves existing local visibility on snapshot replacement. Undiscovered calendars and unrelated old events are not cleared.

## Verification

Environment used for every valid Gradle run:

- `JAVA_HOME=D:\FM\work\jbr`
- `ANDROID_HOME=C:\Users\victo\AppData\Local\Android\Sdk`
- `GRADLE_USER_HOME=C:\Users\victo\.gradle`

Affected suites:

```text
.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorTest" --tests "*RoomCalendarRepositoryTest" --tests "*CalendarDatabaseMigrationTest" --tests "*ICloudCalDavGatewayTest" --tests "*Ical4jIcsParserTest"
BUILD SUCCESSFUL in 14s
107 tests, 0 failures, 0 errors, 0 skipped
```

Fresh verification matrix:

```text
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks
BUILD SUCCESSFUL in 34s
90 actionable tasks: 90 executed
117 unit tests, 0 failures, 0 errors, 0 skipped
app-debug.apk generated
app-debug-androidTest.apk generated
```

Repository validation:

```text
git diff --check
exit 0
```

The fresh build emitted the host's SDK XML/JDK native-access notices and lint reported 16 non-blocking existing project warnings (dependency/tool updates, manifest/icon, and KTX suggestions). No lint error occurred. Instrumentation tests were assembled, not run on a device, as required by the task command.

## Files

- Synchronization: `sync/SyncCoordinator.kt`, `sync/DefaultSyncCoordinator.kt`, `sync/SyncModels.kt`, `sync/SyncMutex.kt`, `domain/ClockProvider.kt`.
- Wiring: `AppContainer.kt`.
- Resource tracking and sync state: `domain/Models.kt`, `domain/CalendarRepository.kt`, `caldav/ParsedEvent.kt`, `data/Entities.kt`, `data/EntityMappings.kt`, `data/EventDao.kt`, `data/RoomCalendarRepository.kt`.
- Migration: `data/CalendarDatabase.kt`, schema `2.json`, and debug schema asset configuration in `app/build.gradle.kts`.
- Tests: `sync/DefaultSyncCoordinatorTest.kt`, `data/RoomCalendarRepositoryTest.kt`, `data/CalendarDatabaseMigrationTest.kt`.

## Concerns

- Android instrumentation tests were compiled and packaged but not executed because no connected-device run was requested.
- Debug builds package the exported Room schemas so the Robolectric migration integration test can construct a true v1 database; the schema files contain structure only, not user data.

## Fix Round 1: changed-resource replacement and ownership validation

### Root cause and RED evidence

- `DefaultSyncCoordinator.refresh` parsed delta upserts only over the initial range, while `RoomCalendarRepository.applyCalendarDelta` deleted only server tombstones. An ordinary changed resource therefore upserted its new master/instances without deleting old instances from the same href; a cancelled resource similarly left old instances behind, and the token advanced in the same otherwise-successful transaction.
- The coordinator independently used `distinctBy` for event and instance identifiers. Two canonical resource hrefs producing the same stable ID were silently resolved by list order, allowing mixed ownership and later data loss.

Changed-resource/coverage RED:

```text
.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorRoomTest" --tests "*DefaultSyncCoordinatorTest.refreshRebuildsChangedResourcesAcrossEveryCommittedCoverageChunk"
BUILD FAILED in 33s
4 tests, 4 failures (AssertionError at lines 86, 72, 99, and 194)
```

The four failures proved that moved and cancelled resources retained stale rows, a forced changed-resource delete failure did not roll back the token because no delete was attempted, and refresh did not parse every committed coverage chunk.

Cross-resource ownership RED:

```text
.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorTest.refreshRejectsTheSameStableEventIdFromTwoCanonicalResourcesWithoutAdvancingToken" --tests "*DefaultSyncCoordinatorTest.refreshRejectsTheSameStableInstanceIdFromTwoCanonicalResourcesWithoutAdvancingToken"
BUILD FAILED in 5s
2 tests, 2 failures (AssertionError at lines 254 and 286)
```

Both requests incorrectly returned success and published the next token before the fix.

### GREEN and behavioral evidence

- `CalendarDelta.replacedResourceHrefs` explicitly distinguishes ordinary changed resources from server tombstones while retaining existing deletion contracts.
- Room unions deleted and replaced hrefs, deletes their existing masters first, and then performs upserts and token persistence inside the existing single transaction. Real Room/coordinator tests prove a moved resource has only its new occurrence, a cancelled resource has no remaining occurrence, and an injected SQLite delete failure preserves both the old occurrence and old token.
- Delta resources are parsed across every committed coverage range, split into parser chunks no longer than 400 days and retaining each range's display zone. With no committed coverage, refresh retains the bounded initial fallback.
- Parsed events and instances retain their canonical resource owner during aggregation. A stable event or instance ID attributed to more than one href, conflicting repeated values, or an instance whose event belongs to another href throws safe invalid-data failure before the repository transaction; tests prove no delta or token is committed.

Focused GREEN commands:

```text
.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorRoomTest" --tests "*DefaultSyncCoordinatorTest.refreshRebuildsChangedResourcesAcrossEveryCommittedCoverageChunk"
BUILD SUCCESSFUL in 16s
4 tests, 0 failures

.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorTest.refreshRejectsTheSameStableEventIdFromTwoCanonicalResourcesWithoutAdvancingToken" --tests "*DefaultSyncCoordinatorTest.refreshRejectsTheSameStableInstanceIdFromTwoCanonicalResourcesWithoutAdvancingToken"
BUILD SUCCESSFUL in 6s
2 tests, 0 failures
```

Affected suites:

```text
.\gradlew.bat testDebugUnitTest --tests "*DefaultSyncCoordinatorTest" --tests "*DefaultSyncCoordinatorRoomTest" --tests "*RoomCalendarRepositoryTest" --tests "*CalendarDatabaseMigrationTest" --tests "*ICloudCalDavGatewayTest" --tests "*Ical4jIcsParserTest"
BUILD SUCCESSFUL in 14s
113 tests, 0 failures, 0 errors, 0 skipped
```

Fresh verification matrix:

```text
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks
BUILD SUCCESSFUL in 43s
90 actionable tasks: 90 executed
123 unit tests, 0 failures, 0 errors, 0 skipped
app-debug.apk generated
app-debug-androidTest.apk generated
```

`git diff --check` exited 0. The build emitted the same non-blocking host native-access/native-library notices. The user-owned `gradle.properties` modification and untracked `gradle/gradle-daemon-jvm.properties` were not modified or included in this fix.

Fix files: `domain/Models.kt`, `data/RoomCalendarRepository.kt`, `sync/DefaultSyncCoordinator.kt`, `sync/DefaultSyncCoordinatorTest.kt`, and the new real-Room `sync/DefaultSyncCoordinatorRoomTest.kt`.

Fix commit: `fix: replace changed calendar resources atomically`.
