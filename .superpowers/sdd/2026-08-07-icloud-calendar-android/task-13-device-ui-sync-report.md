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
