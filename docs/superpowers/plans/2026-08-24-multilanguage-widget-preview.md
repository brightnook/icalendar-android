# Four-language Support and Widget Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the Android calendar use follow-system, Chinese, English, or Japanese consistently in the app and widget, and show an accurate static 2-by-3 preview in the widget picker.

**Architecture:** Put pure `AppLanguage`-to-`Locale` mapping in the domain layer so Compose and widget code share the same rule. Persist language choices in the existing DataStore, collect them through the settings view model, and use a localized configuration context whenever widget RemoteViews or the widget configuration activity reads visible strings. Add a static preview-only layout to the provider XML; its text is resource-based and never uses user data.

**Tech Stack:** Kotlin, Android Compose Material 3, DataStore Preferences, Android App Widgets/RemoteViews, Robolectric, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-08-24-multilanguage-widget-preview-design.md`

## Global Constraints

- Supported choices are `FOLLOW_SYSTEM`, `CHINESE`, `ENGLISH`, and `JAPANESE` only.
- Follow system maps Chinese locales to Simplified Chinese, Japanese to Japanese, and all other locales to English.
- Preserve existing stored `FOLLOW_SYSTEM` values and the device 12/24-hour preference.
- Widget picker preview is static, 2-by-3, and must contain no account or calendar data.
- Do not install on a device without first notifying the user.
- Do not stage `gradle.properties`, Gradle/Android caches, or unrelated existing widget/theme changes.

---

### Task 1: Shared language model, locale resolution, and catalogs

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/AppLocaleResolver.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/domain/AppSettings.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/AppLanguage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Create: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/test/java/com/victor2319/ioscalendar/ui/AppLanguageTest.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/data/AppLanguageResourceTest.kt`

**Interfaces:**
- Produces: `fun resolveAppLocale(language: AppLanguage, systemLocale: Locale): Locale` in the domain package.
- Produces: enum values `CHINESE`, `ENGLISH`, and `JAPANESE` in `AppLanguage`.

- [ ] **Step 1: Write failing locale and catalog tests**

```kotlin
assertEquals(Locale.JAPANESE, resolveAppLocale(AppLanguage.FOLLOW_SYSTEM, Locale.JAPANESE))
assertEquals(Locale.SIMPLIFIED_CHINESE, resolveAppLocale(AppLanguage.CHINESE, Locale.US))
assertEquals(Locale.ENGLISH, resolveAppLocale(AppLanguage.ENGLISH, Locale.JAPANESE))
assertEquals(Locale.JAPANESE, resolveAppLocale(AppLanguage.JAPANESE, Locale.US))
```

Add a Robolectric resource test that loads `R.string.settings_language_japanese` using `Locale.JAPANESE` and expects the Japanese label `日本語`.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.victor2319.ioscalendar.ui.AppLanguageTest --tests com.victor2319.ioscalendar.data.AppLanguageResourceTest`

Expected: compilation failure for missing enum values/resource or assertion failure for the prior Japanese-to-English mapping.

- [ ] **Step 3: Implement the shared mapping and complete resources**

Move the pure locale decision from `ui/AppLanguage.kt` to `domain/AppLocaleResolver.kt`; make the Compose wrapper import it. Extend `AppLanguage`, add language-option labels, and provide every application string in `values-ja/strings.xml`. Keep base values English and Chinese values under `values-zh-rCN`.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit the isolated task**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/domain/AppLocaleResolver.kt app/src/main/java/com/victor2319/ioscalendar/domain/AppSettings.kt app/src/main/java/com/victor2319/ioscalendar/ui/AppLanguage.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-ja/strings.xml app/src/test/java/com/victor2319/ioscalendar/ui/AppLanguageTest.kt app/src/test/java/com/victor2319/ioscalendar/data/AppLanguageResourceTest.kt
git commit -m "feat: add Chinese English and Japanese locales"
```

### Task 2: Language selector and immediate app localization

**Files:**
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/androidTest/java/com/victor2319/ioscalendar/ui/settings/SettingsScreenTest.kt`
- Modify: `app/src/test/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: four-value `AppLanguage` and existing `SettingsViewModel.selectLanguage(language)`.
- Produces: `language-picker` dropdown and `language-option-<enum>` test tags.

- [ ] **Step 1: Write failing selector and persistence tests**

```kotlin
composeRule.onNodeWithTag("language-picker").performClick()
composeRule.onNodeWithTag("language-option-JAPANESE").performClick()
assertTrue(selected == AppLanguage.JAPANESE)
```

Add a view-model test that selects `AppLanguage.JAPANESE`, advances the dispatcher, and asserts both `settings.settings.value.language` and `uiState.value.language` are Japanese.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests com.victor2319.ioscalendar.ui.settings.SettingsViewModelTest`

Expected: missing dropdown option/tag or failed selection assertion. Do not run instrumentation tests on a device.

- [ ] **Step 3: Implement the dropdown**

Replace the single-action `LanguagePicker` with the same `TextButton` plus `DropdownMenu` pattern used by `WeekStartPicker`. Render all `AppLanguage.entries`, label them through resources, and only invoke `selectLanguage` after a chosen menu item. Keep `SettingsViewModel.selectLanguage`'s existing changed-only save notification behavior.

- [ ] **Step 4: Run tests to verify they pass**

Run the command from Step 2. Expected: unit tests pass and Android test sources compile.

- [ ] **Step 5: Commit the isolated task**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsScreen.kt app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModel.kt app/src/androidTest/java/com/victor2319/ioscalendar/ui/settings/SettingsScreenTest.kt app/src/test/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add manual app language selection"
```

### Task 3: Localize widget updates, rows, and configuration activity

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetLocaleContext.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetUpdater.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/widget/AgendaWidgetFactory.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetConfigActivity.kt`
- Modify: `app/src/test/java/com/victor2319/ioscalendar/widget/WidgetDateTimeFormatterTest.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/widget/WidgetLocaleContextTest.kt`

**Interfaces:**
- Consumes: `resolveAppLocale(AppLanguage, Locale)` and `SettingsStore.settings`.
- Produces: `WidgetLocaleContext.resolve(context, appLanguage): Context` and localized widget date/time rendering.

- [ ] **Step 1: Write failing widget locale tests**

```kotlin
assertEquals(Locale.JAPANESE, widgetLocale(AppLanguage.JAPANESE, Locale.US))
assertTrue(WidgetDateTimeFormatter.formatDate(date, zone, Locale.JAPANESE).contains("8月"))
```

Add a test that resolves `FOLLOW_SYSTEM` using `Locale.JAPANESE` and expects Japanese, not English.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.victor2319.ioscalendar.widget.WidgetDateTimeFormatterTest --tests com.victor2319.ioscalendar.widget.WidgetLocaleContextTest`

Expected: missing locale-context API or prior widget code continues to use the raw system context.

- [ ] **Step 3: Implement localized widget contexts**

Create `WidgetLocaleContext` to create a configuration context from the resolved app locale. In `WidgetUpdater` and `AgendaWidgetFactory`, read `settings.language`, use the localized context for `getString`, and pass its locale into each formatter. Build `WidgetConfigActivity` after asynchronously loading the setting so its title, range labels, and save button use the same localized context. Preserve `DateFormat.is24HourFormat` from the original device context.

- [ ] **Step 4: Run tests to verify they pass**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit the isolated task**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/widget/WidgetLocaleContext.kt app/src/main/java/com/victor2319/ioscalendar/widget/WidgetUpdater.kt app/src/main/java/com/victor2319/ioscalendar/widget/AgendaWidgetFactory.kt app/src/main/java/com/victor2319/ioscalendar/widget/WidgetConfigActivity.kt app/src/test/java/com/victor2319/ioscalendar/widget/WidgetDateTimeFormatterTest.kt app/src/test/java/com/victor2319/ioscalendar/widget/WidgetLocaleContextTest.kt
git commit -m "feat: localize widget content"
```

### Task 4: Accurate static 2-by-3 widget picker preview

**Files:**
- Create: `app/src/main/res/layout/agenda_widget_preview.xml`
- Modify: `app/src/main/res/xml/agenda_widget_info.xml`
- Modify: `app/src/test/java/com/victor2319/ioscalendar/widget/WidgetProviderResourcesTest.kt`

**Interfaces:**
- Consumes: current `targetCellWidth=2`, `targetCellHeight=3`, widget colors, and string resources.
- Produces: `android:previewLayout="@layout/agenda_widget_preview"` and a resource-only preview layout.

- [ ] **Step 1: Write the failing provider-resource test**

```kotlin
assertEquals(
    "@layout/agenda_widget_preview",
    parser.getAttributeValue(ANDROID_NAMESPACE, "previewLayout"),
)
```

Also load `R.layout.agenda_widget_preview` through Robolectric and assert it contains a header and two preview event-title views.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.victor2319.ioscalendar.widget.WidgetProviderResourcesTest`

Expected: missing preview-layout attribute/layout resource.

- [ ] **Step 3: Create the static preview and declare it**

Create a compact light-theme `agenda_widget_preview.xml` that uses resource text for the schedule title/date and two non-personal sample agenda rows. Add `android:previewLayout` to `agenda_widget_info.xml`; retain the existing size, resize, and target-cell declarations.

- [ ] **Step 4: Run the test to verify it passes**

Run the command from Step 2. Expected: provider resource tests pass.

- [ ] **Step 5: Commit the isolated task**

```powershell
git add app/src/main/res/layout/agenda_widget_preview.xml app/src/main/res/xml/agenda_widget_info.xml app/src/test/java/com/victor2319/ioscalendar/widget/WidgetProviderResourcesTest.kt
git commit -m "feat: add two by three widget picker preview"
```

### Task 5: Integration verification and device handoff

**Files:**
- Modify only if verification exposes a defect in Tasks 1-4.

**Interfaces:**
- Consumes: all production changes from Tasks 1-4.
- Produces: a debug APK ready for user-approved device validation.

- [ ] **Step 1: Run all local unit tests and build artifacts**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`

Expected: exit code 0. If an existing Robolectric dependency initialization issue recurs, record affected classes and rerun the focused tests for Tasks 1-4 before reporting the environment issue.

- [ ] **Step 2: Review changed scope and formatting**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only task files plus known pre-existing local changes are present.

- [ ] **Step 3: Notify before any device validation**

Ask the user to connect the phone and explicitly allow a data-preserving `adb install -r`. Do not run `connectedDebugAndroidTest`.

- [ ] **Step 4: Perform user-visible device checks after permission**

Check Chinese, English, Japanese, and follow-system language behavior in the settings page, main calendar, detail sheet, and widget. Open the system widget picker and verify the preview is 2-by-3 before adding it.

- [ ] **Step 5: Commit only task-owned changes after verification**

```powershell
git add app/src/main/java/com/victor2319/ioscalendar/domain app/src/main/java/com/victor2319/ioscalendar/ui/AppLanguage.kt app/src/main/java/com/victor2319/ioscalendar/ui/settings app/src/main/java/com/victor2319/ioscalendar/widget app/src/main/res/values app/src/main/res/values-zh-rCN app/src/main/res/values-ja app/src/main/res/layout/agenda_widget_preview.xml app/src/main/res/xml/agenda_widget_info.xml app/src/test/java/com/victor2319/ioscalendar app/src/androidTest/java/com/victor2319/ioscalendar/ui/settings
git commit -m "feat: complete multilingual widget experience"
```
