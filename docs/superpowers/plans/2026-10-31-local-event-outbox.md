# Local Event Outbox Implementation Plan

> For agentic workers: use subagent-driven-development or executing-plans.

Goal: immediate durable local save with one background submission and safe manual retry.
Architecture: Room outbox overlays remote read models; uploader reconciles conditional
PUT/MOVE before retiring overlays. Existing online repository retains delete behavior.
Tech stack: Kotlin, Room, WorkManager, OkHttp, Compose.
Spec: ../specs/2026-10-31-local-event-outbox-design.md

Global constraints: preserve schema-8 user data; no publication/merge/SDK changes;
no real calendar mutation without disposable test-account authorization; trilingual UI.

Review focus: process death after successful write; concurrent draft saves; server
refresh races; account identity changes; deletion or conflict decisions losing drafts.

## Tasks

- [x] Protocol: implement getResource and conditional MOVE; MockWebServer tests
  must cover Destination, If-Match, Overwrite:F, missing source, unsupported and
  multi-status responses, trusted origin and collection membership.
- [x] Durable storage and uploader: schema migration, outbox DAO/codec, read overlays,
  queued create/update, recovery/readback, conflicts and manual retry, worker and
  AppContainer wiring. Test offline save, restart persistence, repeated submission,
  account changes and snapshot/delta overlay safety before integration.
- [x] UI: status labels in agenda/detail, manual retry entry, conflict comparison
  and explicit resolution, editor busy guard. Test statuses and decision callbacks.
- [x] Integration/review: resolve API mismatches, run unit/build/migration suites,
  fresh review of failure handling, then connected checks if device is available.

Ruling: work in the existing non-main update branch, preserving prior unrelated
theme/inset fixes and local artifacts; do not commit or stage unrelated files.

Local verification: 434 unit tests, zero failures/errors/skips; assembleDebug and
assembleDebugAndroidTest succeeded. Connected acceptance and real disposable
iCloud CRUD/MOVE have not run because no device was connected.

Later signed-in emulator acceptance completed: physical offline save/process
restart, failed manual upload, reconnect without automatic failed-write retry,
successful real cloud create/edit/MOVE/delete, both real ETag-conflict choices,
and themed UI screenshots. See docs/internal-testing/2026-10-31-local-event-outbox-verification.md.
