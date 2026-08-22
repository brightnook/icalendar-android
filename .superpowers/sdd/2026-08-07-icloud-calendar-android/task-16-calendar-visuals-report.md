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
