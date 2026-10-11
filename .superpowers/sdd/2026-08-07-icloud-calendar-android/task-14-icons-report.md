# Task 14 — icon assets and launcher icon report

## Implemented assets

- The supplied green `#2EBE71` vector drawables are assigned to the calendar
  View, date-jump, refresh, and settings bottom actions. Their outer action
  buttons retain content descriptions and Material 48 dp minimum touch targets.
- The supplied 1254×1254 calendar artwork is the source for the xxxhdpi
  adaptive-icon foreground. The foreground is a high-quality resampling of
  the centered calendar cutout, with the edge-connected red tile removed to
  transparency. Its visible bounds are x=105–328 and y=102–335 in the
  432×432 source, inside Android's 84–348 safe zone. The separate red backing
  drawable, not the foreground image, supplies the launcher background.

## Test-first evidence

- The focused instrumented tests first failed as expected on device because
  `bottom-view-icon` was not rendered and the application had no app-owned
  launcher icon.
- Those tests could not be rerun after the implementation: the controller
  restored the device app and directed that no further ADB/device operations
  occur during this task.

## Local verification

- `:app:assembleDebug :app:testDebugUnitTest` completed successfully.
- `aapt2 dump badging` confirms every density uses
  `res/mipmap-anydpi-v26/ic_launcher.xml`.
- The packaged adaptive XML references the app-owned red background and the
  packaged xxxhdpi calendar foreground.
- `LauncherIconAssetTest` protects the transparent safe-zone boundary so the
  foreground cannot regress to an edge-to-edge red tile.
- `git diff --check` passed; Gradle emitted existing dependency/runtime
  deprecation warnings and an existing WorkManager manifest-merge warning.

## Device verification pending

The device must be explicitly cleared for a later non-destructive update and
visual check of the launcher asset and four bottom icons. No ADB operation was
issued after the controller’s stop direction.
