# Four-language Support and Widget Preview Design

## Goal

Deliver Chinese, English, and Japanese as selectable application languages, retain follow-system behavior, and make the Android widget picker preview accurately represent the default 2-by-3 widget.

## Language behavior

The persisted `AppLanguage` setting will contain `FOLLOW_SYSTEM`, `CHINESE`, `ENGLISH`, and `JAPANESE`.

- Follow system maps any Chinese system locale to Simplified Chinese, Japanese to Japanese, and every other locale to English.
- Manual language choices override the device locale.
- The localized Compose context remains the single source for main-app text, weekday labels, and date formatting.
- All application-visible strings will have `values`, `values-zh-rCN`, and `values-ja` resources. Base resources remain English.

## Settings and persistence

The current language row becomes a dropdown matching the sync-mode and week-start controls. A choice persists through `SettingsStore.setLanguage`, updates the active Compose locale immediately, and shows the existing saved confirmation only when it changes.

Existing stored `FOLLOW_SYSTEM` values remain valid, so app upgrades preserve users' current behavior.

## Widget localization

Widget updates and `RemoteViewsFactory` load the persisted app setting and resolve it through the same locale-mapping policy used by the app. Header dates, agenda date groups, all-day text, and widget resource strings therefore use the selected language rather than the raw device locale. The system's 12/24-hour preference remains unchanged.

## 2-by-3 picker preview

Add a static preview layout dedicated to Android's widget picker. It will depict the existing light widget hierarchy at the default 2-by-3 proportions: compact header, date strip, and representative two-line agenda content. It contains no account or calendar data. `agenda_widget_info.xml` will reference it with `android:previewLayout` while retaining `targetCellWidth=2` and `targetCellHeight=3`.

## Tests and verification

- Unit tests cover locale resolution for the four language modes and persistence compatibility.
- Settings UI tests cover all language options.
- Widget formatter/updater tests verify the selected application locale reaches header and agenda dates.
- Resource tests ensure the Japanese string catalog contains all required application strings.
- Build the debug APK. Before any device install, notify the user; verify Chinese, English, Japanese, follow-system, and picker preview on device after permission.

## Out of scope

- Additional languages beyond Chinese, English, and Japanese.
- Per-widget language settings.
- Dynamic or account-derived content in the system widget picker preview.
