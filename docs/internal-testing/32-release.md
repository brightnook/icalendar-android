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
