# NookCalendar

NookCalendar is an independent Android calendar app and home-screen widget for iCloud Calendar, using CalDAV and an Apple Account app-specific password.

中文说明：NookCalendar 是独立的 Android 日历应用和桌面小组件，使用 Apple 账户的应用专用密码连接 iCloud 日历。

This README describes the code in the current branch. Features and behavior may differ from the version currently available on Google Play.

## Features

- Monthly and annual calendar views, event details, and a resizable upcoming-events widget.
- Manual or scheduled synchronization; background execution timing depends on Android and device restrictions.
- Local notifications for supported iCloud event reminders. Free users receive the earliest reminder per event occurrence; Premium supports multiple reminders.
- Premium event creation, editing, deletion, and moving events between writable calendars.
- New and edited events are saved locally first and uploaded in the background. Unsynced changes survive restarts; failed uploads can be retried manually and conflicts require an explicit choice.
- Chinese, English, and Japanese interfaces; light, dark, and custom-image themes.
- Free version with ads; Premium removes ads.

## Requirements

- Android 9 (API 28) or later.
- An Apple Account with iCloud Calendar enabled.
- An [Apple Account app-specific password](https://account.apple.com/), generated in the Apple Account security settings.
- Internet access for iCloud synchronization and Google Play purchase functions. Notifications require the applicable Android permissions.

Use an app-specific password, not the Apple Account primary password. After an Apple Account password reset, an app-specific password may need to be generated again.

## Data and security

- Calendar data, pending changes, settings, and an optional theme image are stored in the app's private local storage.
- The Apple Account and app-specific password are encrypted with a key held in Android Keystore before local storage.
- Calendar synchronization connects directly to iCloud. The app also integrates Google Mobile Ads and Google Play Billing; relevant Google services handle advertising and purchases.
- Unsynced changes are retained locally for their account; they are not a substitute for a successful iCloud upload. Removing the app also removes its local data.

The repository's [privacy policy draft](docs/PRIVACY_POLICY.md) and [older release checklist](docs/GOOGLE_PLAY_RELEASE.md) predate current functionality and should be reviewed before reuse.

## Current limitations

- The editor does not create or modify recurrence rules, and recurring events cannot be edited through the ordinary editor.
- Pending changes cannot be edited or deleted until their upload or conflict is resolved. Check failed or conflicted items and retry or resolve them before relying on iCloud having the latest version.
- Calendar writes require a writable iCloud calendar; network availability and Android background restrictions can delay synchronization.

## Build from source

1. Open the project in Android Studio, or set the Android SDK path in `local.properties`. The project compiles against SDK 36 and targets Java 17; the Gradle daemon toolchain is configured in `gradle/gradle-daemon-jvm.properties`.
2. Build and test on Windows:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
   ```

3. The APK is generated under `app\build\outputs\apk\debug\`.

Release packaging requires a valid AdMob application ID supplied as the `adMobAppId` Gradle property or in local `local.properties`, plus an untracked `keystore.properties` with the release signing configuration. Keep signing credentials and local configuration out of version control. A missing or invalid AdMob application ID can prevent the app from starting.

## Support

For support or privacy questions, contact [vf3210552@gmail.com](mailto:vf3210552@gmail.com).
