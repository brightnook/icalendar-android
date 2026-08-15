# Task 9 report: Calendar, agenda, and modal event details

## Status

DONE

## Implemented

- Added a lifecycle `MainCalendarViewModel` backed by `viewModelScope` and the repository's real calendar, selected-day, event-detail, and coverage flows. Day and event selection use `flatMapLatest`, so old queries are cancelled.
- Added exact local-month coverage checks per visible calendar. A range counts only when its zone, start, and exclusive end exactly equal the requested month; one `EnsureMonth` is issued when any visible calendar is missing that coverage.
- Added fail-closed manual refresh handling for scheduler initialization, disabled sync, and unexpected errors without exposing exception text.
- Added a locale-aware six-week month grid above a persistent selected-day agenda. It uses the system first day of week, system date/12-or-24-hour formatting, all-day-first ordering, calendar colors/names, event title/time, and optional location.
- Added one in-place Material modal sheet for agenda and launch-target details. It shows title, calendar, start/end, all-day status, location, notes, and localized human recurrence including interval and weekdays. Closing it preserves the selected date and remembered agenda list state.
- Added strict external-action parsing for whole-value HTTP(S), telephone, and numeric geo values. Only validated sealed actions can create `ACTION_VIEW`; unsupported schemes and malformed values are ignored, and missing/forbidden handlers are handled without logging values.
- Added defensive `MainActivity` launch-extra parsing and duplicate-delivery suppression. The real `AppRoot` replaces the authenticated placeholder with a stable ViewModel-owned main screen while preserving the existing login-only test seam.
- Added English and Simplified Chinese calendar/detail/sync resources using system typography and Material theme colors.

## TDD evidence

### Main state RED

Command:

```powershell
.\gradlew.bat --no-daemon --console=plain testDebugUnitTest --tests '*MainCalendarViewModelTest'
```

After correcting one test-local constructor typo and rerunning, `compileDebugUnitTestKotlin` failed only on the intentionally absent Task 9 contracts: `MainCalendarViewModel`, `MainCalendarMessage`, `CalendarLaunchTarget`, `CalendarLaunchTargetDelivery`, `ExternalAction`, and `parseExternalAction`. This was the expected focused RED before production implementation.

### State GREEN

The focused command passed after the minimal state/launch/safety implementation:

```text
BUILD SUCCESSFUL in 19s
7 tests, 0 failures/errors
```

One intermediate behavior failure expected `SYNC_NOT_READY` before the test scheduler advanced the derived combined flow. The test now advances the real coroutine flow before observing its UI result; production exception mapping was not bypassed.

### Recurrence RED/GREEN

The focused recurrence test first failed compilation because `RecurrenceDescription`, `RecurrenceFrequency`, and `parseRecurrenceRule` did not exist. It protects interval and ordinal weekday parsing with hand-derived literals. The subsequent complete focused suite passed 8/8.

### UI test-first and device limitation

`MainCalendarScreenTest` was authored before the Compose production files. It covers fixed month-over-agenda geometry, all-day ordering, opening/closing the shared sheet, selected-date and list-position preservation after scrolling to index 15, launch-target result semantics, large-font/dark-mode semantics, and Simplified Chinese resources. The current host has no device/emulator; these tests were compiled and packaged into the Android-test APK, not executed, as required by the task constraints.

## Verification

Focused state plus Android-test packaging after final UI changes:

```text
testDebugUnitTest --tests '*MainCalendarViewModelTest' lintDebug assembleDebugAndroidTest
BUILD SUCCESSFUL in 41s
8 focused tests, 0 failures/errors
```

Affected JVM suite before the final fresh run:

```text
testDebugUnitTest
154 tests, 0 failures/errors before the final recurrence case
```

Required fresh matrix:

```text
testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks
BUILD SUCCESSFUL in 1m 2s
90 actionable tasks: 90 executed
155 JVM tests, 0 failures, 0 errors, 0 skipped
lint: 0 errors, 27 advisory warnings
app-debug.apk and app-debug-androidTest.apk generated
```

`git diff --check` exited zero. Fresh output retained pre-existing host/SDK, WorkManager manifest, native-library stripping, old login-test opt-in, and dependency/KTX advisory warnings; no lint or compilation error remained.

## Files changed

- Main state and launch contracts: `ui/main/MainCalendarState.kt`, `MainCalendarViewModel.kt`, `CalendarLaunchTarget.kt`.
- UI: `ui/main/MainCalendarScreen.kt`, `MonthGrid.kt`, `AgendaList.kt`, `EventDetailSheet.kt`.
- App integration: `ui/AppRoot.kt`, `MainActivity.kt`.
- Resources: English and Simplified Chinese `strings.xml`.
- Tests: `MainCalendarViewModelTest.kt`, `MainCalendarScreenTest.kt`.

## Self-review

- Confirmed no separate event detail destination exists; both agenda taps and launch targets select/open the same sheet state.
- Confirmed the agenda `LazyListState` stays remembered behind the sheet and its instrumented contract scrolls before open and checks the same item after close.
- Confirmed target parsing rejects malformed dates and blank instance IDs, duplicate delivery is suppressed, and consumption clears suppression so a later user delivery can open again.
- Confirmed safe actions cannot be created by embedded text or unsupported schemes, and exception values are never copied into state or logs.
- Confirmed `gradle.properties` and `gradle/gradle-daemon-jvm.properties` are user-owned and remain outside the task staging scope.

## Concerns

- No device/emulator was available, so runtime Compose instrumentation tests were not executed. Their compilation and APK packaging are verified; no runtime pass is claimed.

## Fix Round 1: review findings

### Verified root causes

- Month coverage used serial `collect`, so a blocked old `EnsureMonth` stayed ahead of the newly displayed month. Its permanent `requestedMonths` set also prevented any partial/failed month from retrying.
- Agenda and coverage read a dynamic `ClockProvider.zoneId` only when date/month/calendar emitted. A timezone-only change therefore left repository day bounds and current-month coverage stale.
- `markConsumed` cleared the only remembered launch target. The same `onNewIntent` reopened a closed sheet, and the activity did not restore delivery identity or an unconsumed pending target through configuration recreation.
- Recurrence parsing retained only the final weekday code and discarded positive/negative ordinal prefixes, so `1MO` and `-1SU` could be displayed as ordinary Monday/Sunday.

### TDD RED/GREEN evidence

- Month priority/retry RED: the two focused tests failed because the blocked August sync was not cancelled for September and a partial August result never retried after leaving and returning. GREEN used `collectLatest`, transient in-flight membership released in `finally`, and success clears the stale safe error. Both tests passed.
- Same-month de-dup RED: changing only a calendar name cancelled and restarted the blocked month sync (`started` became 2). GREEN derives a distinct sorted set of visible calendar IDs for coverage; metadata-only changes no longer restart work.
- Timezone RED: the test failed compilation because no explicit zone `StateFlow` input existed. GREEN proves Tokyo-to-UTC with an unchanged selected date switches the observed agenda and rejects Tokyo coverage for the UTC month. The production source is application-scoped, receives `ACTION_TIMEZONE_CHANGED`, and refreshes on activity resume, so a retained ViewModel does not hold an obsolete activity flow.
- Launch delivery RED: after accept and consume, the same target was accepted again. GREEN keeps a durable accepted set while allowing a different target. A second RED failed on missing `savedState`/`fromSavedState`; GREEN serializes date plus Base64 instance identity, ignores malformed state, restores the delivered set, and separately restores an unconsumed pending target.
- Ordinal recurrence RED: `RecurrenceDay` did not exist. GREEN preserves ordinal and weekday, localizes first/last and other positive/negative occurrences for monthly/yearly rules, and returns no parsed description for ordinal daily/weekly rules so the UI uses its accurate generic fallback.
- The large-font Android test now explicitly supplies Compose `fontScale = 1.8f` while retaining dark theme semantics.

Focused final command:

```text
testDebugUnitTest --tests '*MainCalendarViewModelTest' --rerun-tasks
BUILD SUCCESSFUL in 40s
14 focused tests, 0 failures/errors
```

Affected verification:

```text
testDebugUnitTest lintDebug assembleDebugAndroidTest
BUILD SUCCESSFUL in 55s
161 JVM tests, 0 failures/errors/skips
lint: 0 errors
```

Final fresh verification after moving the timezone source to application scope:

```text
testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks
BUILD SUCCESSFUL in 1m 7s
90 actionable tasks: 90 executed
161 JVM tests, 0 failures, 0 errors, 0 skipped
lint: 0 errors, 27 advisory warnings
debug and Android-test APKs generated
```

`git diff --check` exited zero. The fresh warnings remain pre-existing SDK XML, manifest-removal, native-library, old login-test opt-in, dependency, and KTX advisories. No connected device/emulator was available, so instrumentation tests were assembled rather than run.

### Fix Round 1 files and self-review

- Updated `CalendarApplication.kt`, `MainActivity.kt`, `AppRoot.kt`, `MainCalendarViewModel.kt`, `CalendarLaunchTarget.kt`, `EventDetailSheet.kt`, English/Chinese strings, and the Task 9 JVM/Android tests.
- Confirmed visible-ID changes, month changes, and zone changes still cancel obsolete coverage work; calendar display-name changes do not.
- Confirmed cancellation rethrows, in-flight state is released in `finally`, partial/failure remains safe, and revisiting can retry.
- Confirmed delivered target state never contains notes, titles, credentials, or exception details and malformed saved values are discarded without logging.
- Confirmed the user-owned `gradle.properties` and `gradle/gradle-daemon-jvm.properties` remain outside staging.
