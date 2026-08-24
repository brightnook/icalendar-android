# Google Play release checklist

This document records the release work still required before publishing iOSCalendar to Google Play. It is a preparation checklist, not evidence that a production release has been completed.

## Current project baseline

- Application ID: `com.victor2319.ioscalendar`
- Minimum Android version: Android 9 (API 28)
- Target Android version: API 36
- Current version: `1.0` (`versionCode` 1)
- Network permission: Internet access only
- Product behavior: read-only iCloud Calendar synchronization and local display

## Before creating a Play release

- [ ] Set the production `versionCode` and `versionName`. Increase `versionCode` for every Play upload.
- [ ] Generate and securely back up an upload key. Keep the keystore, passwords, and any signing configuration outside this repository.
- [ ] Configure release signing locally or in a secure CI environment.
- [ ] Build a signed Android App Bundle with `./gradlew.bat :app:bundleRelease`.
- [ ] Install and test the release build on supported Android versions and form factors.
- [ ] Verify sign-in, manual synchronization, scheduled synchronization, logout, cache clearing, themes, and both widget sizes.
- [ ] Confirm the release contains no debug-only configuration, test credentials, screenshots, APK/AAB files, keystores, or local SDK configuration.

## Play Console listing

- [ ] Prepare a store listing in each supported language: Chinese, English, and Japanese.
- [ ] Prepare the required application icon, feature graphic, phone screenshots, and widget screenshots.
- [ ] Publish the privacy policy at a stable public HTTPS URL, then add that URL to the Play Console listing. The source policy is [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
- [ ] Add the support email: `vf3210552@gmail.com`.
- [ ] Accurately complete Play Console's Data safety form. The app handles account credentials and calendar information only to provide iCloud synchronization; it has no advertising or analytics SDKs.
- [ ] Complete the app-content declarations, including the target audience, content rating, and any required account-deletion or data-deletion disclosures.

## Suggested release verification

1. Create a closed internal-test track first.
2. Test fresh installation, iCloud app-specific-password sign-in, sync, widget placement, logout, and re-login.
3. Test light, dark, and image-background themes with and without applying the theme to the widget.
4. Review Android vitals and tester feedback before expanding the release track.

## Repository hygiene

The repository ignores local Android/Gradle caches, generated verification screenshots, build artifacts, local SDK configuration, and common signing-key formats. Never commit a real Apple app-specific password, keystore, release bundle, or local `local.properties` file.
