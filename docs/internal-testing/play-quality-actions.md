# Play quality actions for the next update

Baseline: main `3ee6e0a`, version 31 (1.0.30).
Branch: `fix/play-quality-actions`.

## Live Console findings (2026-10-26)

All three recommendations now identify version 31. These replace the earlier
version-23-only observations in bitmap-memory-investigation.md.

1. Edge-to-edge: enable backward-compatible edge-to-edge and verify insets.
2. Deprecated cutout constants: `cn7.onApplyWindowInsets`, `m84.h`, `pn7.U`, and
   `com.google.android.play.core.hsdp.service.HsdpShimActivity.onCreate`.
3. Network bitmap decode: `lw5.p0`, `BitmapFactory.decodeStream` and
   `URLConnection.getInputStream`.

The version-31 release mapping resolves the obfuscated classes to:

- cn7: com.google.android.gms.ads.internal.util.zzw
- m84: com.google.android.gms.ads.internal.overlay.zzag
- pn7: com.google.android.gms.ads.internal.util.zzx
- lw5: com.google.android.gms.internal.ads.zzelp

The release manifest merger identifies HsdpShimActivity as coming from
com.google.android.play:hsdp:2.0.1. These findings do not identify application
wallpaper decoding as the source of the network-bitmap recommendation.

## Changes

- Explicit AndroidX enableEdgeToEdge in MainActivity and WidgetConfigActivity.
- Widget configuration combines system-bar and display-cutout insets on all
  four sides, retaining its existing content padding.
- MainActivity updates status/navigation icon appearance from the resolved app
  theme rather than the system night-mode setting. Missing wallpapers use the
  resolved white fallback. This also runs after an app-theme change.
- Existing Compose screens already handle status/navigation bar padding;
  actual screen transitions and navigation modes still require device checks.

## Third-party findings

Do not remove SDK activities or modify third-party bytecode to hide warnings.
Ads SDK is 25.4.0. Official release notes list 25.5.0, but do not document a fix
for these specific cutout or bitmap paths. An SDK update alone cannot be
claimed to resolve them. Keep these recommendations open pending a verified
SDK fix or a separately tested migration.

Reference: https://developers.google.com/admob/android/rel-notes

### SDK binary check (2026-10-28)

Inspected the locally cached official play-services-ads 25.5.0 AAR using
`javap -c -p`, without modifying SDK bytecode or changing app dependencies.

- Its cutout handling still writes layoutInDisplayCutoutMode values 1 and 2
  (SHORT_EDGES and NEVER). The deprecated cutout paths therefore still exist
  after a minor-version upgrade.
- `com.google.android.gms.internal.ads.zzemc` still calls URL.openConnection,
  URLConnection.getInputStream, and the single-argument BitmapFactory.decodeStream,
  then NotificationCompat.Builder.setLargeIcon. This is SDK notification-icon
  loading, not application wallpaper or widget-preview decoding.
- Consequently no application bitmap-loader change or 25.5.0-only bump is a
  demonstrated fix for the remaining recommendations.
- Official next-generation migration replaces the SDK dependency and changes
  initialization (explicit app ID, mandatory background-thread initialization)
  and banner APIs/callback behavior. It is a separate migration requiring ad
  load, consent, purchase/ad suppression, layout and lifecycle regression tests;
  it has not been implemented or claimed to clear Play warnings here.

Migration reference: https://developers.google.com/admob/android/next-gen/migration

## Release gate

### Time picker contrast follow-up (2026-10-31)

User screenshot reproduced a partial-color-scheme error: dark editor text
was combined with inherited light surface-container roles. A regression test
failed with BLACK onSurface/surfaceContainerHigh contrast of 1.22495:1.
The editor now starts from a complete light or dark Material3 color scheme
according to the resolved app theme, then applies the existing app palette.
This preserves picker behavior and avoids the inherited light containers.
The regression test covers white/black/image themes and six foreground/background
pairs used by picker surfaces and controls, requiring at least 4.5:1 contrast.
Targeted tests, full unit suite and assembleDebug passed. Device visual
verification remains pending because no emulator was connected during this run.

Follow-up screenshots also identified dropdown and delete-button contrast.
Calendar/reminder dropdowns inherit the completed color scheme; regression
coverage now includes surfaceContainer against both menu text roles. The delete
button explicitly pairs errorContainer/onErrorContainer and uses readable
surfaceContainerHighest/onSurfaceVariant colors when disabled. Contrast checks
cover these pairs for all three themes. Targeted editor and deletion-confirmation
tests and assembleDebug passed after these changes; visual acceptance remains
pending. Disabled state and confirmation-before-delete behavior are unchanged.

Local validation: `:app:testDebugUnitTest :app:assembleDebug` succeeded;
401 unit tests, zero failures, zero errors, zero skipped tests.
Covered-install of debug app and test APK succeeded on emulator-5554, preserving
app data. EventEditorScreenTest passed all four tests: status-bar clearance,
approved fields/actions, and date/time pickers with a language override.
These component tests do not validate MainActivity window behavior or all
navigation/cutout/theme combinations listed below.

Follow-up: emulator black theme showed almost-black status icons. Window state
confirmed StatusBar was shown with a 142px top inset while appearance was
LIGHT_STATUS_BARS / LIGHT_NAVIGATION_BARS. After the theme-appearance fix,
assembleDebug and CalendarThemePresentationTest passed, and a fresh emulator
screenshot showed white time, Wi-Fi and battery icons above the calendar,
with no main-screen overlap. White/image themes and modal windows still need
visual acceptance; this is not a complete navigation-mode matrix.

No version bump or publication yet. Verify on a connected emulator/phone:
main calendar, settings, login, event editor, widget configuration, keyboard,
gesture navigation, three-button navigation, light/dark/image themes, and
cutout/landscape layouts. Confirm all controls remain reachable and background
and system-bar icon contrast remains readable. Recheck the resulting release
in Play Console; do not promise all recommendations disappear.
