# Task 10 report

Implemented settings sync modes, calendar visibility, current-month history cleanup, full cache deletion, masked account status, confirmation dialogs, and serialized local logout.

TDD evidence:
- Settings RED: temporarily removed Task 10 production APIs; `SettingsViewModelTest` failed in `compileDebugUnitTestKotlin` on missing settings/account/history APIs. Restored minimal implementation; focused settings test passed.
- Login route RED: temporarily removed the initial-success authentication callback; the new focused test failed (1/1 assertion). Restored callback; focused settings/login/account/scheduler suite passed.
- Logout RED: temporarily removed the signed-out transition; `LocalAccountManagerTest` failed (1/1 assertion). Restored it; focused suite passed.
- Settings UI RED: after correcting an unavailable test assertion, temporarily removed `SettingsScreen`; `assembleDebugAndroidTest` failed only for the missing screen. Restored screen and APK compilation passed.
- AppRoot injection RED: the new rendering test failed to compile before the authentication-state overload existed. The overload was added and APK compilation passed. No Android device was connected, so its runtime rendering tests were not executed; this is an assembly-only limitation.

Verification commands: focused `testDebugUnitTest` suites passed; `assembleDebugAndroidTest` passed. Final fresh `--no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --rerun-tasks` completed after its task-scoped Java processes exited. The generated unit XML reports 171 tests, 0 failures, and 0 errors; lint report and both debug APKs were produced. The terminal wrapper output detached before printing Gradle's final banner, but the completed process plus fresh reports/APKs provide the terminal evidence.

Changed task files include settings UI/viewmodel, account manager, Room history APIs, routing, strings, and focused unit/android tests. `gradle.properties` and `gradle/gradle-daemon-jvm.properties` are automatic files and intentionally excluded.

Concerns: Android instrumentation tests compile but were not run because no device was attached. Build output retains existing manifest and native-strip warnings.

## Fix Round 1

- Restored accounts now route to authenticated calendar content independently of the LoginViewModel's default `LOGIN` destination. `AppRootRoutingTest` was first RED with the routing predicate absent, then GREEN.
- Settings has an explicit localized Close control and system back handler; the Android UI test was first RED with `onClose` absent, then its original APK assembly passed. The subsequent fresh assembly retry after final resource edits was blocked by a task-owned debug-keystore lock. Runtime Android rendering remains device-only and was not executed because no device is attached.
- `SyncScheduler.cancelAll` is suspending and WorkManager cancellation awaits completion under a mutex. The deterministic logout race test was RED after temporarily reordering credentials before cancellation (`[credentials, cancel]`), then GREEN with the required `cancel -> credentials -> cache` sequence. Cache cleanup failure remains a safe signed-out result.
- Added Room recurring-instance history test. It was RED after temporarily omitting the history deletion call, then GREEN after restoring it: past instance is purged while the current instance and recurring master remain, and coverage starts at the explicit boundary.

Round-1 focused GREEN evidence: `testDebugUnitTest` for AppRoot routing, LocalAccountManager (2 tests), and the Room recurring-history test. The isolated cache download was required for Robolectric's Android runtime. Subsequent Android-test assembly retry hit a task-owned debug-keystore lock; no Android device was available for runtime instrumentation.

## Device verification follow-up

ADB device `aaf08081` was online. The current debug and test APKs installed successfully. Running `AppRootAuthenticationRouteTest` on-device executed both tests but both failed before assertions because the test package cannot resolve the Compose fixture activity: `Unable to resolve activity ... com.victor2319.ioscalendar.test/androidx.activity.ComponentActivity`. This is an Android-test manifest/fixture issue, not an AppRoot authentication-route failure. No production or test code was changed during this verification-only follow-up; Settings instrumentation was not run because it uses the same Compose fixture and would fail at the same setup point.
