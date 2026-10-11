# Task 13 — device UI and empty-sync diagnosis

## Delivered scope

- Both login and calendar content consume the top system status-bar inset.
- The month grid remains seven weighted columns (at most one physical-pixel remainder on a width not divisible by seven); date labels are centered, one line, and no longer have duplicated button padding that clips two-digit dates.
- A vertical drag changes month in the expected direction. Previous/next buttons and custom accessibility actions expose the same behavior; date selection remains independent.
- Refresh and Settings now have an explicit 8dp visual gap and both remain present at large font scale.
- The sync-error slot now lives inside the lower area, so an error cannot move the fixed month/agenda split.
- A zero-calendar discovery result no longer navigates to an unexplained empty calendar. It remains on login with safe, actionable text, and clears the just-saved credential so a restart cannot silently route to the same empty state.
- Kept the inherited Task 10 device-test fixture repair: `ui-test-manifest` plus real `MainActivity` rules are required for Compose instrumentation packaging/launch.

## RED → GREEN evidence

1. Empty discovery RED: `LoginViewModelTest.successfulAuthenticationWithNoDiscoveredCalendarsStaysOnLoginWithGuidance` initially failed with expected `LOGIN`, actual `CALENDAR`.
2. Empty discovery GREEN: the targeted unit test passed after the zero-calendar branch and safe message were added.
3. Device UI RED sources: the connected-phone feedback showed date clipping and adjacent Refresh/Settings controls. The first device class run also showed the status test was mounted outside the app shell and the equal-column test had a locale/rounding fixture defect; these were corrected without reverting the resolved UI behavior.
4. Device UI GREEN: the final focused direct instrumentation run completed **7/7**:
   - status-bar inset
   - seven-column layout
   - two-digit date bounds
   - vertical swipe followed by date selection
   - Refresh/Settings gap
   - large-font/dark-theme core controls
   - unchanged month/agenda split when a sync message appears
5. Full local verification: `:app:testDebugUnitTest --no-daemon` produced 19 XML suites with 0 failures and 0 errors (175 tests).
6. `:app:assembleDebug` and `:app:assembleDebugAndroidTest` both passed after the final changes.

## Device execution (serial `aaf08081`)

- Device: `24117RK2CG`, 1440×3200.
- Initial replacement attempts reported `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the installed production and test packages had different signing keys. The user explicitly approved removal after being told it would erase local app cache and Android Keystore credentials. The main task removed only `com.victor2319.ioscalendar` and `com.victor2319.ioscalendar.test`.
- Current debug app and instrumentation companion installed successfully. The final direct `am instrument` run above returned `OK (7 tests)`.
- No credentials, tokens, calendar items, message bodies, screenshots, or UI text from the user’s data were read, exported, or logged.

## Safe empty-sync diagnosis

The reproducible code root cause was discovery returning zero calendars: `DefaultSyncCoordinator` produces `SyncResult.Success(SyncCounts(calendars = 0))`, while the previous login flow treated every success as complete navigation. The new app-visible result says that iCloud accepted the connection but no calendars were discovered, directs the user to verify iCloud Calendar is enabled, and permits a clean retry.

Login already separates rejected credentials, network errors, and server failures; Settings exposes calendar visibility/selection. This task intentionally did not re-enter or inspect the user’s credentials after the approved reset, so it does not claim the prior phone’s actual account had zero discovered calendars rather than another externally visible failure.

## Remaining concern

The full `MainCalendarScreenTest` class previously included an unrelated existing simplified-Chinese resource assertion that failed on this device (`关闭` not displayed). It is outside the focused task requirements and was not changed. The final targeted six-test device suite is green.

## Follow-up: live partial-first-sync diagnosis and repair

### Safe diagnosis

- The user reported the login error “部分日历未能完成首次同步”. I scoped log inspection to the app process and did not inspect stored data, credentials, headers, URLs, calendar titles, or events.
- The safe runtime stack identified `net.fortuna.ical4j.model.ZoneRulesProviderImpl` failing with `NoSuchMethodError` for Android's hidden `java.time.zone.ZoneRulesProvider` constructor. The route was `ICloudCalDavGateway.validateCalendarData` → `fetchRange`.
- Authentication and discovery had therefore completed; each range fetch of a calendar containing `VTIMEZONE` failed before parsing. `DefaultSyncCoordinator` classified the linkage error as `REPOSITORY`, returned `SyncResult.PartialSuccess`, and LoginViewModel showed the generic partial-sync message. This is the reproducible root cause, not a credential, login, discovery, selection, or empty-calendar failure.

### Minimal fix

- Removed the redundant gateway `CalendarBuilder` pre-parse. It still enforces the byte limit and a cheap `VCALENDAR`/`VEVENT` envelope guard; `Ical4jIcsParser` remains the authoritative structural, event, and recurrence validator.
- Constructed that parser with no iCal4j time-zone registry, so canonical TZIDs resolve through Android's platform zone database rather than registering ical4j's unsupported provider.
- When a `PartialSuccess` includes at least one usable calendar, the login page keeps its error and retry control and adds an explicit, localized “查看日历” / “View calendars” action. It is user-initiated; an all-failed partial result still has no calendar action. No zero-calendar result is presented as synced.
- Added only test/debug loopback cleartext support for a synthetic `MockWebServer`; release continues to require HTTPS.

### RED → GREEN evidence for this follow-up

1. **RED on serial `aaf08081`:** before production changes, two focused instrumentation tests failed: the `VTIMEZONE` parser threw the same `NoSuchMethodError`, and the partial-sync screen had no calendar action.
2. **GREEN:** after the fix, a targeted instrumentation run returned `OK (3 tests)` for:
   - `Ical4jIcsParserInstrumentedTest` (valid `VTIMEZONE` parsing);
   - `ICloudCalDavGatewayInstrumentedTest` (synthetic valid range fetch does not pre-parse through the incompatible provider);
   - `LoginScreenTest.partialFirstSyncKeepsItsWarningAndOffersCalendarView`.
3. **Local unit GREEN:** `LoginViewModelTest` passed 9/9, including user-initiated routing for a usable partial result and the no-action all-failed case.
4. A broader 20-test device class selection had 13 passes and 7 failures. The three new tests and all relevant month-grid/split/action tests passed. The seven failures are pre-existing `AppRoot`/localized-resource fixture assumptions (the default `AppRoot(factory)` renders its authenticated placeholder), not this sync fix; they are recorded rather than masked.

### Device/account limitation

The current debug app and test APK installed successfully over the existing debug install. I did not submit, replay, retrieve, or inspect any Apple Account credential, nor trigger a real iCloud request after the update. The user can now press the app's retry control to perform the final account-specific sync check themselves. The synthetic device gateway test proves the previously failing Android code path is fixed, but does not claim a fresh live iCloud result without that user action.

## Follow-up: month event markers and final device-feedback polish

### Delivered

- A populated local date now has up to three small calendar-color markers beneath its date number. Markers are computed only from visible calendars; an empty date has no marker. All-day and timed events that span local dates mark every covered date, clamped to the displayed month.
- Selected date backgrounds are a fixed 32dp circle, so single- and two-digit dates use the same size and centered geometry.
- Refresh and Settings are now 48dp icon controls (circular arrow and gear) with their localized names retained as accessibility content descriptions. The Settings close control is an accessible 48dp X icon.
- Settings uses the top status-bar inset, has a fixed header/close action, and scrolls its body so **Sign out / 退出登录** remains below Delete cache and can be reached with long calendar lists. Its existing confirmation explicitly says it removes saved credentials; AppRoot invokes `requestLogout(clearCache = false)`, so cached calendar data is not cleared by default.
- Event details are bounded to the sheet, internally scrollable at large font sizes, and padded above the navigation-bar inset so long title/location/notes and the close action remain reachable.
- The user separately confirmed that real iCloud calendar data is visible on the device. No calendar names, items, account identifiers, or credentials were inspected in making that record.

### RED → GREEN evidence

1. **RED reports:** the device feedback supplied the initial failing cases: clipped two-digit dates, variable selection geometry, overly-close text buttons, top-crowding on Settings, long detail content inaccessible near the bottom, and Sign out below the Settings viewport.
2. **RED test development:** the new marker aggregation test initially failed on marker ordering. The requirement is three distinct colors rather than a presentation order, so the assertion was corrected to be order-independent; the test keeps the important multi-day, hidden-calendar, duplicate-color, cap-at-three, and empty-day cases.
3. **GREEN local unit suite:** `:app:testDebugUnitTest` completed successfully (including `MonthMarkersTest` and `MainCalendarViewModelTest.monthMarkersObserveOnlyVisibleCalendarsForTheDisplayedMonth`).
4. **GREEN APK compilation:** `:app:assembleDebug :app:assembleDebugAndroidTest` completed successfully with the new Compose tests compiled. The instrumentation coverage includes marker nodes/cap, fixed selected-circle bounds, icon-only accessible actions, detail scrolling at 1.8x font scale, month swipe/selection, settings safe header, and scrolling to/clicking Sign out with credential-only confirmation text.

### Device result and limitation

- Serial `aaf08081` was online. A no-data-loss `adb install -r` attempt was correctly rejected for both packages with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the installed app/test packages are signed with a different key from this worktree's APKs.
- I did **not** uninstall or clear the installed app, because that would erase the now-working local credentials/cache and no new approval was given for this later signing conflict. The ensuing runner invocation exercised only the old installed test package, so it is not counted as verification of the new follow-up tests. It did re-confirm the already-installed vertical-month swipe test passed; an old Settings fixture failed because its old `MainActivity` test rule calls `setContent` after the activity has set content. This follow-up changes that test fixture to `ComponentActivity`.
- Consequently, this round has compiled instrumentation coverage and prior device evidence, but not a valid fresh-device green result for the newly built APK. A data-preserving install requires an APK signed with the installed key; otherwise a user-approved uninstall/re-login is required. No account data was accessed while diagnosing the signing restriction.

## Final visual follow-up

- Centered the selected-date label vertically with the 48dp Refresh and Settings icon controls in the lower agenda header; the regression compares the label and both icon centers.
- Saturday and Sunday headers and unselected date numbers now use the theme-aware tertiary color. The selected state retains `onPrimary` for contrast; the unit test covers weekend, weekday, and selected-weekend precedence.
- Calendar visibility color swatches are clipped to circular 14dp dots. The Compose regression captures the node image and verifies its corner is not colored like its center, catching a return to a square swatch.
- Local verification after these changes: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` completed; 179 unit tests, 0 failures, 0 errors. The Compose tests compiled into the instrumentation APK.
- No device reinstall was attempted in this follow-up because the existing signed app retains successful live credentials/cache and the new APK has the recorded signing mismatch. The limitation remains deliberate and non-destructive.

## Settings-section cards follow-up

- Settings now keeps its safe-area, fixed header and scrollable body while grouping content into four localized cards: Account / 账户, Sync mode / 同步方式, Calendars / 日历, and Data and sign out / 数据与退出登录.
- Each card is a rounded 20dp `surfaceVariant` at 72% opacity, so the treatment follows light/dark theme colors while remaining visibly separated by the existing 12dp list spacing. The existing masked account, switches, radio buttons, destructive-action confirmation, and Sign out reachability remain unchanged.
- Added Compose section semantics tags and an instrumentation regression requiring all four sections. Local verification: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`; 179 unit tests, 0 failures, 0 errors; app and instrumentation APKs compiled.
- No device reinstall was attempted due the documented signing mismatch and preservation of the working account/cache.

## Bottom action bar follow-up

- Added a fixed, navigation-bar-safe five-action icon bar: Today, combined/year view toggle, date picker, Refresh, and Settings. Refresh and Settings no longer appear in the agenda header.
- Default is combined month plus agenda. Year mode shows all twelve miniature months and reuses available day markers; tapping a year-day or date-picker result selects it and returns to combined view. Today selects the clock-zone current date.
- The new APKs compiled locally. Device replacement was not attempted because it would not preserve the currently working signed installation's credentials/cache.

### Subsequent device verification

- A later data-preserving `adb install -r` updated both debug and test APKs successfully on `aaf08081`; no uninstall, cache clearing, credential access, or real iCloud action occurred.
- Focused instrumentation result: `MainCalendarScreenTest.bottomActionBarExposesFiveAccessibleIconActions` and `tappingADayInYearViewReturnsToCombinedViewWithThatDateSelected` returned `OK (2 tests)`.
