# Task 15: PNG icon replacement

## Delivered

- Imported the supplied Chinese-style action art as optimized 144px `xxxhdpi` PNG drawables.
- Mapped the main bottom actions to Today, view switch, search/date jump, refresh, and settings PNGs.
- Replaced the Settings close and sign-out visuals, plus the login action visual, with the supplied PNGs while preserving the accessible labels and 48dp action sizing.
- Replaced the adaptive launcher foreground with the supplied red calendar artwork, retained the adaptive XML/background, and kept all opaque foreground pixels within the 84..347 safe zone.

## TDD evidence

- RED: `PngActionIconResourcesTest` failed as intended with `Missing PNG drawable ic_action_today` before the action assets were restored.
- RED: `LauncherIconAssetTest` failed as intended because the previous foreground did not occupy the new artwork sample point inside the safe zone.
- GREEN: `PngActionIconResourcesTest` and `LauncherIconAssetTest` both passed after the minimal asset/UI changes.
- Full verification: `:app:testDebugUnitTest :app:assembleDebug` completed successfully.

## Device install attempt

ADB detected device `aaf08081`. A non-destructive `adb install -r` of the debug APK was attempted after the successful build, but Android rejected it with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the installed package has a different signing certificate. No uninstall, data removal, or other device mutation was performed; visual inspection of this APK was therefore not possible.
