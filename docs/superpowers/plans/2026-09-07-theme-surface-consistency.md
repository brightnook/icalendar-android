# Theme Surface Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply selected themes to every in-app surface and replace opaque Android widget IDs with readable, stable labels.

**Architecture:** `CalendarThemePresentation` becomes the sole source of colors for main content and modal surfaces. Widget configurations gain a persisted display ordinal; the system `appWidgetId` remains internal and continues to drive configuration and updates.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, JUnit/Robolectric, Android Gradle.

**Spec:** `docs/superpowers/specs/2026-09-07-theme-surface-consistency-design.md`

## Global Constraints

- Do not add theme modes or change wallpaper import, sync, language, Premium, reminders, or widget-refresh behavior.
- Image wallpapers remain behind the calendar only; sheets use a dark readable surface.
- The widget remains light unless its existing apply-theme option is enabled.
- `appWidgetId` is never displayed to users and remains the configuration/refresh/delete key.
- Widget labels do not renumber when another widget is removed.

---

### Task 1: Add resolved modal and Settings palette values

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/theme/CalendarThemePresentation.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/theme/CalendarThemePresentationTest.kt`

**Interfaces:**
- Produces `settingsContainerColor`, `settingsSectionColor`, `modalContainerColor`, `modalScrimColor`, `outlineColor`, and `actionContentColor` on `CalendarThemePresentation`.
- `AppRoot` passes its already-resolved `themePresentation` to `SettingsScreen`.

- [ ] **Step 1: Write the failing palette tests**

```kotlin
@Test fun blackThemeUsesDarkSettingsAndModalSurfaces() {
    val p = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null)
    assertThat(p.settingsContainerColor).isEqualTo(Color(0xFF080A0D))
    assertThat(p.modalContainerColor).isEqualTo(Color(0xFF111318))
}

@Test fun imageThemeUsesReadableModalSurface() {
    val p = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.IMAGE), File("present"))
    assertThat(p.modalContainerColor.alpha).isGreaterThan(0.9f)
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.theme.CalendarThemePresentationTest`

Expected: compile failure because the new fields do not exist.

- [ ] **Step 3: Implement the immutable palette**

```kotlin
data class CalendarThemePresentation(
    // existing properties
    val settingsContainerColor: Color,
    val settingsSectionColor: Color,
    val modalContainerColor: Color,
    val modalScrimColor: Color,
    val outlineColor: Color,
    val actionContentColor: Color,
)
```

Use white opaque values in white mode, opaque near-black values in black mode, and translucent settings cards with opaque modal sheets in image mode. Preserve all existing main-calendar values.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.theme.CalendarThemePresentationTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/brightnook/nookcalendar/ui/theme/CalendarThemePresentation.kt app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt app/src/test/java/com/brightnook/nookcalendar/ui/theme/CalendarThemePresentationTest.kt; git commit -m "feat: add shared themed surface palette"`

### Task 2: Theme Settings and feedback surfaces

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/settings/ThemeSettingsSection.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/settings/SettingsPresentationPolicyTest.kt`

**Interfaces:**
- Consumes `themePresentation: CalendarThemePresentation` in `SettingsScreen`.
- Produces Settings cards, controls, snackbar, and confirmation dialog colored from that presentation.

- [ ] **Step 1: Write failing Compose presentation tests**

```kotlin
@Test fun blackThemeSettingsDoesNotRenderWhiteSectionSurface() {
    // Render SettingsScreen with black presentation and assert the theme section color is dark.
}

@Test fun imageThemeKeepsSettingsControlsReadable() {
    // Render image presentation and assert title/action semantics remain visible.
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.settings.SettingsPresentationPolicyTest`

Expected: assertions fail before palette routing.

- [ ] **Step 3: Apply the palette explicitly**

```kotlin
fun SettingsScreen(
    state: SettingsUiState,
    themePresentation: CalendarThemePresentation,
    // existing callbacks
)
```

Use `settingsContainerColor` at the root, `settingsSectionColor` in `SettingsSection`, and resolved content/outline/action colors for picker buttons, switches, snackbars, and `AlertDialog`. Keep existing callbacks and test tags.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.settings.SettingsPresentationPolicyTest --tests com.brightnook.nookcalendar.ui.settings.ThemeSettingsViewModelTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/brightnook/nookcalendar/ui/settings/SettingsScreen.kt app/src/main/java/com/brightnook/nookcalendar/ui/settings/ThemeSettingsSection.kt app/src/test/java/com/brightnook/nookcalendar/ui/settings/SettingsPresentationPolicyTest.kt; git commit -m "feat: apply themes to settings surfaces"`

### Task 3: Theme event-detail and date-picker sheets

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/main/MainCalendarScreen.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/main/EventDetailSheet.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/main/DatePickerTitleTest.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/main/EventDetailSheetTest.kt`

**Interfaces:**
- Consumes the existing `themePresentation` parameter of `MainCalendarScreen`.
- Produces modal container, scrim, text, outline, and action colors for both sheets.

- [ ] **Step 1: Write failing sheet-color tests**

```kotlin
@Test fun datePickerUsesThemeModalContainerAndScrim() {
    // Render with black presentation and assert the picker root reports dark colors.
}

@Test fun eventDetailUsesImageThemeColors() {
    // Render with image presentation and assert title and close action use readable colors.
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.main.DatePickerTitleTest --tests com.brightnook.nookcalendar.ui.main.EventDetailSheetTest`

Expected: compilation or assertion failure before new palette inputs are added.

- [ ] **Step 3: Route palette values through both sheets**

```kotlin
EventDetailContent(
    detail = detail,
    zoneId = zoneId,
    contentColor = themePresentation.contentColor,
    secondaryContentColor = themePresentation.secondaryContentColor,
    surfaceColor = themePresentation.modalContainerColor,
    premium = premium,
)
```

Pass `modalContainerColor`, `modalScrimColor`, `outlineColor`, and `actionContentColor` to the date-picker grid. Preserve selected/today behavior and localized date formats.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.main.DatePickerTitleTest --tests com.brightnook.nookcalendar.ui.main.EventDetailSheetTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/brightnook/nookcalendar/ui/main/MainCalendarScreen.kt app/src/main/java/com/brightnook/nookcalendar/ui/main/EventDetailSheet.kt app/src/test/java/com/brightnook/nookcalendar/ui/main/DatePickerTitleTest.kt app/src/test/java/com/brightnook/nookcalendar/ui/main/EventDetailSheetTest.kt; git commit -m "feat: theme calendar detail and date picker sheets"`

### Task 4: Add stable widget display ordinals

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/data/Entities.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/data/CalendarDatabase.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/data/WidgetConfigDao.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/widget/RoomWidgetPreferences.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/widget/WidgetConfigViewModel.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Test: `app/src/test/java/com/brightnook/nookcalendar/widget/WidgetConfigViewModelTest.kt`
- Test: `app/src/androidTest/java/com/brightnook/nookcalendar/data/CalendarDatabaseMigrationTest.kt`

**Interfaces:**
- Produces `WidgetManagementItem(appWidgetId: Int, displayOrdinal: Int, range: WidgetRange)`.
- Produces `WidgetConfigEntity.displayOrdinal: Int` and `MIGRATION_6_7`.

- [ ] **Step 1: Write failing ordinal and migration tests**

```kotlin
@Test fun managementItemsUseOrdinalInsteadOfAndroidId() {
    val items = widgetManagementItems(
        intArrayOf(44, 92),
        mapOf(44 to config(44, ordinal = 1), 92 to config(92, ordinal = 2)),
    )
    assertThat(items.map(WidgetManagementItem::displayOrdinal)).containsExactly(1, 2)
}
```

Add an instrumentation migration test which creates a version-6 `widget_configuration`, runs `MIGRATION_6_7`, and verifies non-null unique `displayOrdinal` values.

- [ ] **Step 2: Run the unit test and confirm it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.widget.WidgetConfigViewModelTest`

Expected: compilation failure because `displayOrdinal` does not exist.

- [ ] **Step 3: Add Room migration and allocation**

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE widget_configuration ADD COLUMN displayOrdinal INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE widget_configuration SET displayOrdinal = appWidgetId WHERE displayOrdinal = 0")
    }
}
```

Raise the schema version to `7`. New configurations get `max(displayOrdinal) + 1`; updates retain their existing ordinal. The migration assigns deterministic temporary values to current rows so no old data is lost; future new labels advance from the maximum.

- [ ] **Step 4: Replace displayed internal ID with localized ordinal**

```kotlin
Text(stringResource(
    R.string.settings_widget_configure,
    widget.displayOrdinal,
    widget.range.label(),
))
```

Use localized copy `Agenda widget %1$d · %2$s`, `日程小组件 %1$d · %2$s`, and `予定ウィジェット %1$d・%2$s`. Preserve `onConfigureWidget(widget.appWidgetId)`.

- [ ] **Step 5: Run focused tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.widget.WidgetConfigViewModelTest`

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.brightnook.nookcalendar.data.CalendarDatabaseMigrationTest`

Expected: PASS; an upgraded app opens without Room integrity failure.

- [ ] **Step 6: Commit**

Run: `git add app/src/main/java/com/brightnook/nookcalendar/data app/src/main/java/com/brightnook/nookcalendar/widget app/src/main/java/com/brightnook/nookcalendar/ui/settings/SettingsScreen.kt app/src/main/res/values app/src/main/res/values-zh-rCN app/src/main/res/values-ja app/src/test/java/com/brightnook/nookcalendar/widget/WidgetConfigViewModelTest.kt app/src/androidTest/java/com/brightnook/nookcalendar/data/CalendarDatabaseMigrationTest.kt; git commit -m "feat: add readable widget management labels"`

### Task 5: Validate visual and upgrade behavior

**Files:**
- Test: existing unit and instrumentation suites.

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: PASS, except only an unchanged, documented pre-existing failure if it reproduces.

- [ ] **Step 2: Build and visually inspect debug APK**

Run: `./gradlew.bat :app:assembleDebug`

Verify white, black, and image Settings; open event detail and date picker in each mode; verify readable actions; verify widget stays light with apply-theme disabled.

- [ ] **Step 3: Verify upgrade and labels**

Install the APK over a version-14 app with an existing widget. Confirm Settings displays `Agenda widget 1 · …`; reconfigure it; remove another widget; reopen Settings and confirm the first label is unchanged.

- [ ] **Step 4: Run static check**

Run: `git diff --check`

Expected: no output.

## Plan self-review

- Tasks 1-3 cover every themed app surface, including fallback/readability rules.
- Task 4 covers stable, localized widget-management labels with an explicit Room migration.
- Task 5 covers visual rendering, upgrade safety, widget independence, and repository quality checks.
