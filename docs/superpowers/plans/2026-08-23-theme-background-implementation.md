# Theme Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add white, black, still-image, and animated-image backgrounds to the main calendar, with an optional static widget rendition.

**Architecture:** Persist a compact `ThemeSettings` value in the existing DataStore-backed `AppSettings`. A `ThemeAssetStore` owns app-private imported files and widget-safe first-frame previews. Compose receives resolved theme state only for `MainCalendarScreen`; `SettingsScreen` remains light. The RemoteViews widget reads a resolved, static presentation and never plays animation.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, DataStore Preferences, Android Photo Picker, Android ImageDecoder, Coil 3 Compose/GIF modules, RemoteViews, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-23-theme-background-design.md`

## Global Constraints

- Support minSdk 28 and targetSdk 36.
- Theme choices are exactly white, black, and imported image/animated image; video is out of scope.
- Imported assets use the system photo picker and app-private storage; never retain a long-lived external URI permission.
- Wallpaper content uses center-crop fill; animated content plays only in the visible main calendar.
- Month and year calendar Saturday labels use blue and Sunday labels use red with theme-specific contrast values; weekday labels retain the normal text color.
- Settings UI remains light and does not use the chosen background.
- Applying a theme to widgets is opt-in and off by default; widgets use a static first frame and preserve readable event surfaces.
- Do not commit or push any changes unless the user explicitly requests it.

---

## File structure

- `domain/ThemeSettings.kt`: domain model for the persisted choice and optional asset key.
- `data/SettingsStore.kt`, `data/DataStoreSettingsStore.kt`: read/write theme settings with safe defaults.
- `theme/ThemeAssetStore.kt`: import, validate, replace, remove, and resolve private theme files.
- `theme/WidgetThemePreview.kt`: first-frame decoding and bounded bitmap preview creation.
- `ui/theme/CalendarBackground.kt`: Compose wallpaper and contrast layer.
- `ui/settings/ThemeSettingsSection.kt`: light settings controls and photo-picker bridge.
- `widget/WidgetThemePresentation.kt`: RemoteViews-safe colors and optional bitmap lookup.

### Task 1: Persisted theme domain and settings API

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/domain/ThemeSettings.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/domain/AppSettings.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/data/SettingsStore.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/data/DataStoreSettingsStore.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/data/ThemeSettingsStoreTest.kt`

**Interfaces:**
- Produces `enum class ThemeBackgroundMode { WHITE, BLACK, IMAGE }`.
- Produces `data class ThemeSettings(val mode: ThemeBackgroundMode = WHITE, val assetKey: String? = null, val applyToWidget: Boolean = false)`.
- Extends `SettingsStore` with `suspend fun setTheme(settings: ThemeSettings)`.

- [ ] **Step 1: Write failing settings round-trip tests.**

```kotlin
@Test fun defaultsToWhiteThemeWithoutWidgetApplication() = runTest {
    assertEquals(ThemeSettings(), store.settings.first().theme)
}

@Test fun persistsImageThemeAndWidgetChoice() = runTest {
    val expected = ThemeSettings(ThemeBackgroundMode.IMAGE, "theme/background.gif", true)
    store.setTheme(expected)
    assertEquals(expected, store.settings.first().theme)
}
```

- [ ] **Step 2: Run the tests and verify the expected missing-type/API failure.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.data.ThemeSettingsStoreTest' --no-daemon`

Expected: compilation fails because `ThemeSettings` and `setTheme` do not yet exist.

- [ ] **Step 3: Add the model and DataStore keys.**

```kotlin
private val themeModeKey = stringPreferencesKey("theme_mode")
private val themeAssetKey = stringPreferencesKey("theme_asset_key")
private val themeApplyToWidgetKey = booleanPreferencesKey("theme_apply_to_widget")

override suspend fun setTheme(settings: ThemeSettings) = dataStore.edit { preferences ->
    preferences[themeModeKey] = settings.mode.name
    preferences[themeApplyToWidgetKey] = settings.applyToWidget
    settings.assetKey?.let { preferences[themeAssetKey] = it } ?: preferences.remove(themeAssetKey)
}
```

Use `runCatching { ThemeBackgroundMode.valueOf(value.orEmpty()) }.getOrDefault(ThemeBackgroundMode.WHITE)` when reading persisted mode.

- [ ] **Step 4: Run the focused tests and full unit suite.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.data.ThemeSettingsStoreTest' :app:testDebugUnitTest --no-daemon`

Expected: all pass.

### Task 2: Private asset import and preview generation

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/theme/ThemeAssetStore.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/theme/WidgetThemePreview.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/theme/ThemeAssetStoreTest.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/theme/WidgetThemePreviewTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces `interface ThemeAssetStore { suspend fun import(uri: Uri): ThemeAssetImport; suspend fun remove(assetKey: String); fun fileFor(assetKey: String): File? }`.
- Produces `sealed interface ThemeAssetImport { data class Success(val assetKey: String) : ThemeAssetImport; data class Failure(val reason: ThemeAssetFailure) : ThemeAssetImport }`.
- Produces `fun createWidgetThemePreview(source: File, destination: File, maxWidthPx: Int, maxHeightPx: Int): Boolean`.

- [ ] **Step 1: Write failing import and first-frame tests.**

```kotlin
@Test fun rejectedImportLeavesExistingAssetUntouched() = runTest {
    val previous = store.createTestAsset("old.png")
    assertEquals(ThemeAssetImport.Failure(ThemeAssetFailure.UNSUPPORTED), store.import(unsupportedUri))
    assertTrue(previous.exists())
}

@Test fun animatedImagePreviewUsesFirstFrameAtBoundedSize() {
    val preview = createWidgetThemePreview(animatedGif, output, 720, 720)
    assertTrue(preview)
    assertTrue(BitmapFactory.decodeFile(output.path).width <= 720)
}
```

- [ ] **Step 2: Run focused tests and verify expected missing symbols.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.theme.*' --no-daemon`

Expected: compilation fails because the asset and preview APIs do not exist.

- [ ] **Step 3: Add Coil dependencies and private-file importer.**

Add `io.coil-kt.coil3:coil-compose:3.5.0` and `io.coil-kt.coil3:coil-gif:3.5.0`. Copy to `filesDir/theme-assets/<random-name>.<extension>` through a temporary file, reject files larger than `20 MiB`, and decode once before atomically replacing the old file. Permit still images, GIF, and animated WebP; reject video MIME types and undecodable files.

- [ ] **Step 4: Implement preview extraction.**

Use `ImageDecoder.createSource(source)` and `ImageDecoder.decodeBitmap` to obtain the first decoded frame. Center-crop/downscale it to the requested bounds and encode PNG into the widget-preview directory. Return `false` without replacing a current preview if decoding or writing fails.

- [ ] **Step 5: Run focused and full tests.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.theme.*' :app:testDebugUnitTest --no-daemon`

Expected: all pass.

### Task 3: Theme controls in the light settings page

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/ThemeSettingsSection.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Create: `app/src/test/java/com/victor2319/ioscalendar/ui/settings/ThemeSettingsViewModelTest.kt`

**Interfaces:**
- `SettingsUiState` adds `val theme: ThemeSettings` and `val themeImportError: ThemeAssetFailure?`.
- `SettingsViewModel` exposes `selectTheme(mode: ThemeBackgroundMode)`, `importTheme(uri: Uri)`, `removeThemeImage()`, and `setApplyThemeToWidget(enabled: Boolean)`.
- `ThemeSettingsSection` accepts `ThemeSettings`, actions, and an Activity Result launcher provided by `SettingsScreen`.

- [ ] **Step 1: Write failing ViewModel tests.**

```kotlin
@Test fun selectingBlackThemeUpdatesPersistedState() = runTest {
    viewModel.selectTheme(ThemeBackgroundMode.BLACK)
    assertEquals(ThemeBackgroundMode.BLACK, store.settings.first().theme.mode)
}

@Test fun failedImageImportRetainsCurrentTheme() = runTest {
    store.setTheme(ThemeSettings(ThemeBackgroundMode.BLACK))
    viewModel.importTheme(badUri)
    assertEquals(ThemeBackgroundMode.BLACK, store.settings.first().theme.mode)
}
```

- [ ] **Step 2: Run focused tests and verify expected API failure.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.ui.settings.ThemeSettingsViewModelTest' --no-daemon`

Expected: compilation fails because the ViewModel theme actions do not exist.

- [ ] **Step 3: Implement settings state and photo picker controls.**

Use `ActivityResultContracts.PickVisualMedia` with `PickVisualMedia.ImageOnly`. Render white and black as selectable controls, an image action, a remove-image action only when an asset exists, and an `Apply to widget` switch. Keep these controls inside the existing translucent Settings section; do not apply wallpaper behind settings.

- [ ] **Step 4: Ensure successful theme mutations refresh widgets only when enabled.**

After each successful `setTheme`, `importTheme`, `removeThemeImage`, or apply-to-widget change, call `widgetUpdater.refreshAll()`. Do not refresh after a failed import.

- [ ] **Step 5: Run focused and full tests.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.ui.settings.ThemeSettingsViewModelTest' :app:testDebugUnitTest --no-daemon`

Expected: all pass.

### Task 4: Main-calendar wallpaper and contrast rendering

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/theme/CalendarBackground.kt`
- Create: `app/src/main/java/com/victor2319/ioscalendar/ui/theme/CalendarThemePresentation.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/main/MainCalendarScreen.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/main/MonthGrid.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/main/YearCalendar.kt`
- Create: `app/src/test/java/com/victor2319/ioscalendar/ui/theme/CalendarThemePresentationTest.kt`

**Interfaces:**
- Produces `data class CalendarThemePresentation(val contentColor: Color, val panelColor: Color, val saturdayColor: Color, val sundayColor: Color, val wallpaperPath: String?)`.
- Produces `fun calendarThemePresentation(theme: ThemeSettings, assetFile: File?): CalendarThemePresentation`.
- `MainCalendarScreen` receives `themePresentation: CalendarThemePresentation`.

- [ ] **Step 1: Write failing presentation tests.**

```kotlin
@Test fun blackThemeUsesLightContentOverDarkPanels() {
    val presentation = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null)
    assertEquals(Color.White, presentation.contentColor)
    assertTrue(presentation.panelColor.alpha < 1f)
}

@Test fun weekendColorsRemainDistinctFromWeekdayTextInEveryTheme() {
    ThemeBackgroundMode.entries.forEach { mode ->
        val presentation = calendarThemePresentation(ThemeSettings(mode), null)
        assertNotEquals(presentation.contentColor, presentation.saturdayColor)
        assertNotEquals(presentation.contentColor, presentation.sundayColor)
        assertNotEquals(presentation.saturdayColor, presentation.sundayColor)
    }
}

@Test fun missingImageFallsBackToWhitePresentation() {
    assertEquals(ThemeBackgroundMode.WHITE, calendarThemePresentation(imageTheme, null).resolvedMode)
}
```

- [ ] **Step 2: Run the focused test and verify expected missing symbols.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.ui.theme.CalendarThemePresentationTest' --no-daemon`

Expected: compilation fails because the presentation API does not exist.

- [ ] **Step 3: Implement the theme resolver and wallpaper composable.**

Use Coil `AsyncImage` behind the combined and year calendar content with `ContentScale.Crop`. Use a bounded semi-transparent content panel for image backgrounds. For black, use dark panel colors and light text. For a missing asset, persist a white fallback through `SettingsViewModel` and render white immediately.

- [ ] **Step 4: Thread colors into existing calendar components without affecting settings.**

Pass explicit presentation colors to `MonthGrid` and `YearCalendar`. Use the resolved Saturday blue and Sunday red in both calendar views, including their weekday headers. Preserve existing selection, today, and event-marker semantics.

- [ ] **Step 5: Run focused and full tests.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.ui.theme.CalendarThemePresentationTest' :app:testDebugUnitTest --no-daemon`

Expected: all pass.

### Task 5: Optional static widget theme

**Files:**
- Create: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetThemePresentation.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/widget/WidgetUpdater.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/widget/AgendaWidgetFactory.kt`
- Modify: `app/src/main/res/layout/agenda_widget.xml`
- Modify: `app/src/main/res/values/widget_colors.xml`
- Modify: `app/src/main/res/values-night/widget_colors.xml`
- Create: `app/src/test/java/com/victor2319/ioscalendar/widget/WidgetThemePresentationTest.kt`

**Interfaces:**
- Produces `data class WidgetThemePresentation(val mode: WidgetThemeMode, val previewFile: File?)`.
- Produces `fun widgetThemePresentation(theme: ThemeSettings, preview: File?): WidgetThemePresentation`.
- `WidgetUpdater` loads theme settings and sets all widgets to either normal light, black, or an image preview with a readable overlay.

- [ ] **Step 1: Write failing widget theme resolution tests.**

```kotlin
@Test fun widgetIgnoresThemeUntilExplicitlyEnabled() {
    assertEquals(WidgetThemeMode.LIGHT, widgetThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK, applyToWidget = false), null).mode)
}

@Test fun imageThemeUsesPreviewOnlyWhenAvailable() {
    assertEquals(WidgetThemeMode.IMAGE, widgetThemePresentation(imageThemeEnabled, preview).mode)
    assertEquals(WidgetThemeMode.LIGHT, widgetThemePresentation(imageThemeEnabled, null).mode)
}
```

- [ ] **Step 2: Run the focused test and verify expected missing symbols.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.widget.WidgetThemePresentationTest' --no-daemon`

Expected: compilation fails because widget theme resolution does not exist.

- [ ] **Step 3: Implement the RemoteViews-safe presentation.**

Add a background `ImageView` under the widget content and use `setImageViewBitmap` only for an imported preview. Keep a translucent light content layer above it. For black, set explicit dark background and light text resources. For all failures and disabled application, reuse the existing light widget presentation.

- [ ] **Step 4: Run focused and full tests.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.victor2319.ioscalendar.widget.WidgetThemePresentationTest' :app:testDebugUnitTest --no-daemon`

Expected: all pass.

### Task 6: Integration verification and device handoff

**Files:**
- Modify: `docs/superpowers/specs/2026-08-23-theme-background-design.md` only if verification reveals a contradiction.
- Test: existing unit and Android instrumentation suites.

- [ ] **Step 1: Run static checks and all unit tests.**

Run: `git diff --check` and `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`.

Expected: no whitespace errors, all unit tests pass, both APKs build.

- [ ] **Step 2: Install only the debug APK as an in-place update after user approval.**

Run: `adb -s <connected-device> install -r app/build/outputs/apk/debug/app-debug.apk`.

Expected: `Success`; preserve local account and calendar data. Do not run device instrumentation tests because they can reinstall or clear the app without an explicit user instruction.

- [ ] **Step 3: Ask the user to check exact acceptance cases.**

Check white/black selection, static center-crop, GIF playback, a rejected import preserving the prior background, theme opt-in on a newly re-added widget, static image fallback on that widget, and settings page remaining light.
