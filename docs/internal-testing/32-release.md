# Internal testing release32 (1.0.31)

Branch: fix/play-quality-actions. Production main remains unchanged pending
real-device internal acceptance. Database migrates8 to9 without deleting user data.

Changes: durable local-first create/edit with background iCloud submission,
manual failed retries, explicit conflict decisions, conditional cross-calendar
MOVE, readable themed editor controls and backward-compatible edge-to-edge.
Ads SDK remains25.4.0; upstream SDK quality recommendations remain tracked.

Validation: 438 unit tests passed for version32. Signed bundleRelease and release
lint checks passed. Release manifest confirms32/1.0.31, stable package/minSdk28/
targetSdk36, and the existing production AdMob application ID. Live test-account
offline/restart/retry, real iCloud create/edit/MOVE/delete and both conflict
decisions passed; all disposable cloud resources were cleaned up. See the
2026-10-31 local-event-outbox verification report for details and limitations.

Release notes: docs/internal-testing/32-release-notes.txt (EN/ja/zh-CN).
Target: Google Play internal testing only. Console track state is the final
publication gate; build/signature verification alone is not publication proof.

## Publication verified

On2026-10-31, uploaded the signed AAB and published to internal testing. Console
track displayed latest32 (1.0.31), active, and "released to internal testers".
Details: https://play.google.com/console/u/0/developers/7166309858145226385/app/4974700495514389761/tracks/4701748470730273188/releases/30/details

Implementation commit7e48b13 is pushed to brightnook/icalendar-android on
fix/play-quality-actions. EN/ja/zh-CN release notes were included. The only
review warning concerned missing native debug symbols; no blocking errors or
device-support reductions were shown. Main and production track were not changed.
