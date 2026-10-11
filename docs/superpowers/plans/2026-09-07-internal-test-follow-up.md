# Internal-Test Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the next NookCalendar build resolve internal-test language, purchase-display, and widget usability regressions without adding Settings-screen theme coverage.

**Architecture:** Preserve the current Compose localized-context model and prove its release behavior with a signed variant. Keep Google Play's `ProductDetails` formatted price as the only price authority, retaining the previous price during background operations. Extend the existing `WidgetUpdater` and `WidgetConfigActivity` rather than adding a second widget configuration system.

**Tech Stack:** Kotlin, Jetpack Compose, Android App Widgets / RemoteViews, Room, WorkManager, Google Play Billing, Robolectric, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-07-internal-test-follow-up-design.md`

## Global Constraints

- Preserve Android 9 minimum compatibility and current Android 16/17 support.
- Do not upload, commit, or log signing keys, passwords, or local AdMob configuration.
- Use the signed release variant, not debug alone, to validate language behavior.
- Price follows the signed-in Google Play storefront and is never converted from the selected display language.
- Settings-screen theme coverage is explicitly out of scope for this release.

---

### Task 1: Reproduce and correct release-language rendering

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/AppLanguage.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/MainActivity.kt`
- Modify: `app/src/test/java/com/brightnook/nookcalendar/ui/LocalizedAppContentTest.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/LocalizedAppContentTest.kt`

**Interfaces:**
- Consumes: `LocalizedAppContent(language: AppLanguage, content: @Composable () -> Unit)`.
- Produces: a locale override that updates `LocalContext`, `LocalResources`, and `LocalConfiguration` with a release-safe localized configuration.

- [ ] **Step 1: Add the release-equivalent failing resource test.**

  Render `LocalizedAppContent(AppLanguage.JAPANESE)` under a non-Japanese base configuration and assert both `settings_title` and `settings_language_japanese` use Japanese resources. Add a second assertion after changing from English to Japanese.

- [ ] **Step 2: Run the focused test before the fix.**

  Run:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.brightnook.nookcalendar.ui.LocalizedAppContentTest" --no-daemon --console=plain
  ```
  Record whether it exposes a resource-context gap. If it passes, retain it as regression coverage and continue with signed release reproduction; do not claim the device issue is reproduced by a debug test.

- [ ] **Step 3: Trace the release resource path and make the smallest correction.**

  Ensure the localized context is built from a configuration whose locale list is explicitly set and that the provided resources/configuration originate from that same context. Preserve Activity result registry ownership and do not call `Activity.recreate()` or `AppCompatDelegate.setApplicationLocales()`.

- [ ] **Step 4: Re-run tests and install a signed release APK on the emulator.**

  Build release with the local signing configuration and install its APK. Test English, Chinese, and Japanese in Settings, then close/reopen Settings after each choice. Expected: all labels and values use the selected language without a black screen, repeated flashing, or navigation reset.

- [ ] **Step 5: Commit the isolated language fix.**

  ```powershell
  git add app/src/main/java/com/brightnook/nookcalendar/ui/AppLanguage.kt app/src/main/java/com/brightnook/nookcalendar/MainActivity.kt app/src/test/java/com/brightnook/nookcalendar/ui/LocalizedAppContentTest.kt
  git commit -m "fix: preserve selected language in release builds"
  ```

### Task 2: Refresh existing widgets and provide reconfiguration

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/widget/WidgetUpdater.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/widget/WidgetConfigActivity.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/brightnook/nookcalendar/widget/WidgetUpdaterTest.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/widget/WidgetConfigViewModelTest.kt`

**Interfaces:**
- Consumes: `WidgetRefreshNotifier.refreshAll()` after sync and the existing `WidgetIntentFactory.configurationIntent(context, appWidgetId)`.
- Produces: `WidgetUpdater.refreshAll()` that refreshes RemoteViews and invalidates collection data for every active ID; Settings action that launches configuration for an active widget ID.

- [ ] **Step 1: Add failing widget-refresh tests.**

  Test that `refreshAll()` calls `updateAppWidget` and `notifyAppWidgetViewDataChanged(ids, R.id.widget_events)` for active IDs after a successful sync. Test that an empty active-ID list does not request an update.

- [ ] **Step 2: Run the widget tests to establish the current behavior.**

  Run:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.brightnook.nookcalendar.widget.*" --no-daemon --console=plain
  ```

- [ ] **Step 3: Add a widget-management Settings entry.**

  Query active IDs through `AppWidgetManager.getAppWidgetIds(ComponentName(...AgendaWidgetProvider::class.java))`. Render an empty-state message if none exist; otherwise render one action per active widget showing its range and open the existing `WidgetConfigActivity` with `EXTRA_APPWIDGET_ID`.

- [ ] **Step 4: Improve the existing configuration screen.**

  Add spacing around the 30-day range control using a layout margin/vertical spacer that leaves each option's minimum touch target intact. Saving must call `WidgetUpdater.refresh(intArrayOf(appWidgetId))` and return the same widget ID.

- [ ] **Step 5: Verify on device state.**

  Add a widget before the first sync, sync events, and confirm the already placed widget updates without removal. Open the in-app management entry, change range/calendar selection, save, and confirm only that widget changes.

- [ ] **Step 6: Commit the widget work.**

  ```powershell
  git add app/src/main/java/com/brightnook/nookcalendar/widget app/src/main/java/com/brightnook/nookcalendar/ui/settings app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt app/src/main/res app/src/test/java/com/brightnook/nookcalendar/widget
  git commit -m "fix: refresh and reconfigure existing widgets"
  ```

### Task 3: Stabilize Google Play price presentation

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/monetization/PlayBillingGateway.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt`
- Modify: `app/src/test/java/com/brightnook/nookcalendar/ui/settings/SettingsPurchaseSupportTest.kt`
- Modify: `app/src/test/java/com/brightnook/nookcalendar/ui/AppRootPurchaseActionTest.kt`

**Interfaces:**
- Consumes: `MonetizationUiState(canShowAds, purchaseState, price)` and Play Billing's `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`.
- Produces: a state transition that retains non-null `price` through LOADING/PENDING actions and renders it without language-based currency conversion.

- [ ] **Step 1: Add the failing price-retention tests.**

  Cover a prior state with `price = "¥390"` followed by loading/restore state: assert `settings_play_price` remains visible and `settings_billing_loading` does not replace it. Add a test proving a supplied `"$2.49"` remains `$2.49` regardless of `AppLanguage`.

- [ ] **Step 2: Run focused tests and confirm failure.**

  Run:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.brightnook.nookcalendar.ui.settings.SettingsPurchaseSupportTest" --tests "com.brightnook.nookcalendar.ui.AppRootPurchaseActionTest" --no-daemon --console=plain
  ```

- [ ] **Step 3: Preserve last successful formatted price.**

  In the billing state owner, keep the last valid `ProductDetails` formatted price while a purchase or restore coroutine is active. In `PurchaseSupportContent`, render the retained price for loading states and disable only the action currently in flight. Do not introduce hard-coded yen/dollar values or a locale-to-currency map.

- [ ] **Step 4: Re-run tests and manual verification.**

  Verify price text stays in place while purchase and restore begin, and that a Play account/storefront offering a different currency determines the display. A language switch alone must not claim to change currency.

- [ ] **Step 5: Commit the purchase-display fix.**

  ```powershell
  git add app/src/main/java/com/brightnook/nookcalendar/monetization app/src/main/java/com/brightnook/nookcalendar/ui app/src/test/java/com/brightnook/nookcalendar/ui
  git commit -m "fix: retain Play price during purchase checks"
  ```

### Task 4: Apply name and final release checks

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/brightnook/nookcalendar/BuildSmokeTest.kt`

**Interfaces:**
- Produces: `NookCalendar` launcher label and a new monotonically increasing Play version code/name.

- [ ] **Step 1: Add a failing app-name/version assertion.**

  Assert the default application-name resource is `NookCalendar` and align `BuildSmokeTest` with the next version code/name.

- [ ] **Step 2: Update localized resources and version.**

  Change all application-name entries to `NookCalendar`; increment version code once for the complete functional-fixes release.

- [ ] **Step 3: Run the release gate.**

  Run focused tests from Tasks 1–4, then:
  ```powershell
  .\gradlew.bat :app:assembleDebug :app:bundleRelease --no-daemon --console=plain
  git diff --check
  ```
  Confirm the signed AAB exists at `app/build/outputs/bundle/release/app-release.aab` and no ignored signing/configuration files are staged.

- [ ] **Step 4: Commit release preparation.**

  ```powershell
  git add app/build.gradle.kts app/src/main/res app/src/test/java/com/brightnook/nookcalendar/BuildSmokeTest.kt
  git commit -m "chore: prepare internal test follow-up release"
  ```

## Plan self-review

- Language release validation: Task 1.
- Stable storefront price presentation: Task 3.
- Existing-widget refresh, range spacing, and cross-launcher reconfiguration: Task 2.
- Consistent app naming and publishable artifact verification: Task 4.
- Settings theme intentionally deferred per the approved scope split.
