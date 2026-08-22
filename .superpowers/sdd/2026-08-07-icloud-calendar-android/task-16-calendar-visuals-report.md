# Task 16: Calendar Visuals Report

## Delivered

- Changed the login and logout account actions to transparent `TextButton` controls. They retain their localized labels, supplied PNG icons, explicit content descriptions, and 48dp minimum touch targets.
- Added a shared visual policy for calendar date cells. Month-grid date boxes now use a fixed 32dp rounded square and always reserve a 6dp marker row, so event dots do not move date digits.
- Updated Year View with a tagged visible year title, 16dp outer horizontal margins, 12dp intra-row mini-month gaps, fixed marker rows, and fixed rounded-square date backgrounds.
- Today and the selected date both use the primary filled rounded-square treatment. When selection is elsewhere, both remain independently visible and use `onPrimary` text for contrast.
- Continued using the supplied PNG action assets; no SVG action assets were reintroduced.

## TDD Evidence

1. RED: `CalendarVisualPolicyTest` was added before its implementation. The focused unit-test run failed at Kotlin compilation because `fixedCalendarDayCellLayout`, `calendarDayHighlight`, `CalendarDayHighlight`, and `fixedYearCalendarLayout` did not exist.
2. GREEN: implemented `CalendarVisualPolicy.kt`; the focused run passed all three policy tests.
3. RED: `ActionControlVisualPolicyTest` was added before its implementation. The focused unit-test run failed at Kotlin compilation because `transparentLabeledActionPresentation` did not exist.
4. GREEN: implemented `ActionControlVisualPolicy.kt` and used it from Login and Settings; the focused run passed.

## Verification

- `:app:testDebugUnitTest --tests com.victor2319.ioscalendar.ui.main.CalendarVisualPolicyTest` — passed, 3 tests.
- `:app:testDebugUnitTest --tests com.victor2319.ioscalendar.ui.ActionControlVisualPolicyTest` — passed.
- `:app:testDebugUnitTest :app:assembleDebug` — passed.
- `:app:compileDebugAndroidTestKotlin` — passed; compilation only, no device was contacted.
- `git diff --check` — passed before commit.

## Scope and Limitations

- The Sunday/Monday week-start preference was not included. It requires a persisted settings/data path plus broader UI coverage, so it remains the next focused task.
- The approved language/weekday-header work is explicitly deferred to its separate follow-up task.
- No ADB, emulator, or physical-device actions were performed because the phone is disconnected. Device visual validation remains pending.
- Existing uncommitted Gradle/cache files (`gradle.properties`, `.android-home/`, `.gradle-user-home/`, and `gradle/gradle-daemon-jvm.properties`) were preserved and are not part of this commit.

## Review Follow-up: Round 1

- Year View mini-day controls now have explicit 48dp minimum targets while retaining compact date-box visuals. Each day exposes its full localized date as a content description.
- Calendar cells and primary app controls use a scoped Material ripple configuration with transient ripple disabled. Persistent selected/today highlights and all semantics remain intact.
- Settings sync mode is now an accessible picker with the current frequency label and menu options for Manual, Hourly, Daily, and Disabled.
- Account masking now reveals up to three Unicode code points without splitting surrogate-pair characters. Logout is located at the right side of the account row; the confirmation and cache-clearing behavior are unchanged.

### Round 1 TDD and Verification

1. RED: `CalendarInteractionPolicyTest`, `SettingsPresentationPolicyTest`, and the Unicode masking assertion in `LocalAccountManagerTest` failed because the date description, 48dp target, ripple policy, picker options, and non-private masking API did not exist.
2. GREEN: the focused JVM test run passed after the minimal implementations were added.
3. Added Compose assertions for YearView day accessibility/touch size, sync dropdown interactions, and right-aligned account logout. They compiled successfully with the Android test source set; they were not executed because no device is connected.
4. `:app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug` completed successfully. The build emitted existing Android SDK/Robolectric future-deprecation warnings only.

### Deferred

- Language/weekday-header and Sunday/Monday week-start changes remain out of scope for this commit.
- No device, emulator, or ADB operation was performed. Stage device validation remains pending before later theme/privacy/release work.
