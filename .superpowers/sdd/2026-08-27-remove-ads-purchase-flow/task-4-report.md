# Task 4 report: integrated ad-supported flow

## Status

Implemented the focused instrumentation regression coverage in `MainCalendarScreenTest`.

The test `pendingPurchaseKeepsBannerVisibleWhileOwnedHidesIt` verifies that a `PENDING` monetization state with `canShowAds = true` exposes `ad-banner-slot`, while confirmed `OWNED` state with `canShowAds = false` exposes no such node.

No production code was changed. The existing `AdBannerSlot` container already exposes `AD_BANNER_SLOT_TAG` (`ad-banner-slot`), so no placement, ad unit, sizing, load-failure collapse, or consent-gating changes were needed.

## Verification

`git diff --check` completed without whitespace errors.

The required instrumentation command was attempted before any production change:

```text
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.victor2319.ioscalendar.ui.main.MainCalendarScreenTest
```

It could not start Gradle because this environment has no `java` executable and no valid `JAVA_HOME`:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
ERROR: JAVA_HOME is set to an invalid directory: "= 1>&2
```

Therefore the instrumentation test did not execute, and no device/emulator availability was reached.

The focused JVM test and debug assembly were also attempted and were blocked by the same Java prerequisite:

```text
./gradlew.bat :app:testDebugUnitTest --tests com.victor2319.ioscalendar.monetization.MonetizationRepositoryTest --no-daemon --console=plain
./gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Neither command reached Gradle test or compilation execution.

## Commit

Commit message: `test: cover ad state after purchase`
