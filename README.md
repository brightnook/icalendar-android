# iOSCalendar

iOSCalendar is an independent Android calendar app and home-screen widget that reads and synchronizes iCloud Calendar data through iCloud CalDAV.

中文说明：这是一个独立的 Android 日历应用与桌面小组件。它使用 Apple Account 的应用专用密码连接 iCloud 日历，数据直接同步到设备本地显示。

## Features

- Monthly calendar with an upcoming-event list, plus an annual calendar view.
- Event detail dialog and date navigation.
- 2 x 3 home-screen widget for upcoming events.
- Manual, hourly, daily, or disabled background synchronization.
- Chinese, English, and Japanese interface languages.
- Light, dark, and optional image-background themes.

## Requirements

- Android 9 (API 28) or later.
- An Apple Account with iCloud Calendar enabled.
- An [Apple Account app-specific password](https://account.apple.com/), generated in the Apple Account security settings.

Do not enter your Apple Account primary password. The app only accepts an app-specific password for iCloud CalDAV sign-in.

## Data and security

- Calendar data, settings, and an optional theme image are stored in the app's private local storage.
- The Apple Account and app-specific password are encrypted with a key held in Android Keystore before local storage.
- Synchronization connects directly between the app and iCloud. This project does not operate an application backend, analytics service, or advertising service.

See the [Privacy Policy](docs/PRIVACY_POLICY.md) for details.

## Current limitations

The app is currently read-only. It does not create, edit, delete, or manage recurrence rules for iCloud calendar events. Event reminders are also not implemented yet.

## Roadmap

Future work may add iCloud event creation and editing as a separately designed feature. Recurrence editing and reminders would be evaluated after that work.

## Build from source

1. Open the project with Android Studio, or configure `local.properties` with a valid Android SDK path.
2. Build a debug APK:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

3. The APK is generated under `app\build\outputs\apk\debug\`.

For release preparation, read the [Google Play release checklist](docs/GOOGLE_PLAY_RELEASE.md).

## Support

For support or privacy questions, contact [vf3210552@gmail.com](mailto:vf3210552@gmail.com).
