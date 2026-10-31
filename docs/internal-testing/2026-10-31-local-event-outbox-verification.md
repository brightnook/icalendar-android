# Local event outbox verification

Branch fix/play-quality-actions; production baseline main 3ee6e0a, version31.
App version is unchanged; database schema advances from8 to9 with migration.

Local run: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
passed: 434 unit tests, zero failures/errors/skips. Includes migration/reopen,
durable offline enqueue, manual-only failed retry, unknown PUT/MOVE readback,
cross-calendar movement, ETag conflicts, deleted-remote resolution, account
isolation, unrelated copies with matching UID and stale old-month reconciliation.
Android instrumentation APK compiles; its tests were not executed on a device.

Existing online delete remains confirmed. Pending drafts temporarily block further
editing/deleting, as disclosed in UI, until sync/conflict resolution finishes.
Cache clear and logout retain account-isolated drafts and now explain retention.
Unparseable or recurring remote conflicts retain local data and block overwrite;
the ordinary-event editor does not resolve these safely in this version.

No connected emulator/phone at final check. No live iCloud writes, real cloud
movement, airplane-mode/device-kill test or visual acceptance has been performed.
Next gate: install over the previous schema8 debug app without uninstalling,
test offline save -> app restart -> reconnect -> manual retry, both conflict
decisions and disposable test-account create/edit/MOVE/delete. Confirm local
reminders, widgets and EN/zh-CN/ja state labels.

No production release, GitHub push/main merge or version bump is authorized by
this implementation step; none was performed.

## Emulator follow-up

Started existing Pixel_901 without wiping data; prior app version31 remained
installed. Covered-install of updated debug/test APKs succeeded and MainActivity
opened with the retained custom wallpaper. Latest full unit/build run passed
438 unit tests, zero failures/errors, plus debug and androidTest APK generation.

Two instrumentation runs passed: 9 tests (PendingEventUiTest4,
EventEditorScreenTest4, WidgetOrdinalMigrationTest1) and 8 tests
(DeviceOutboxPersistenceTest1, ThemedEditorDeviceTest3, PendingEventUiTest4).
This is13 distinct device tests, with PendingEventUiTest rerun after final build.

The isolated real-device SQLite test verified durable local enqueue without
network calls, DB close/reopen, failed state, no background retry of failed work,
manual retry after simulated successful-write/reply-loss without duplicate create,
and overlay retirement after canonical reconciliation. It uses simulated transport,
not real airplane mode or iCloud. Themed screenshots show readable dark time
picker, dropdown and enabled delete button. A test-only idle wait was added for
settled screenshot capture; its latest APK is built, rerun deferred to avoid
interrupting interactive account login.

Actual MainActivity currently shows Connect iCloud Calendar. User requested to
sign in to a test account before live cloud/real network-recovery acceptance.
No real iCloud write or user-event deletion has occurred. Real process-kill/
WorkManager recovery and real cloud MOVE are not yet verified.

## Signed-in live acceptance (completed later on 2026-10-31)

User explicitly confirmed the emulator TEST account was signed in. Added opt-in
LiveOutboxDeviceTest and LiveOutboxConflictTest. All writes used UUID-marked
disposable events; original account events were not edited or deleted.

- Disabled emulator airplane/Wi-Fi/mobile connectivity, saved locally, force-stopped
  the app, restarted it offline and verified the same pending event remained.
  Local save measured841ms (first run) and89ms (warm run), not a performance SLA.
- Restored original radio settings in a PowerShell finally block. Verified airplane
  mode0 and Wi-Fi1 afterward; host computer networking was not changed.
- Real iCloud queued create/edit/MOVE/delete round trip passed in60.753s. Independent
  CalDAV GET verified source absence/destination presence after MOVE, same UID and
  marker ownership, correct edits and deletion from both collections.
- A second disposable event was saved offline and manual sync while offline
  produced FAILED while retaining local contents. After reconnect, waited for
  startup WorkManager jobs to finish and verified FAILED remained until explicit
  manual retry. That retry plus create/edit/MOVE/delete passed in69.144s.
- A separate real iCloud ETag-conflict test used an isolated local Room cache.
  It verified remote changes were not overwritten before resolution, Keep Local
  conditionally saved the chosen local version, and Use Remote preserved the
  chosen server version without replaying local edits. Passed in17.144s; its
  sole disposable resource was independently verified deleted in finally.
- Initial live ownership harness compared raw ICS text with an unescaped semicolon
  and stopped safely before editing/deleting. Switched the guard to parsed exact
  UID, unique title prefix and exact notes; resumed the same test event successfully.
  No application code change was needed for that harness assertion.
- Settled screenshot capture rerun passed all3 themed-editor tests. Inspected
  black/image time dialog, calendar dropdown and enabled delete-button screenshots.
- Final test-account read refresh passed in18.545s and returned app to MainActivity.
  Both live-outbox journals were removed after their successful cleanup; all3
  disposable cloud events across the runs were verified removed.

Unit suite remains438/438 green; later changes in this phase only add device test
harnesses and verification documentation. This confirms ordinary-event flows on
this emulator/account, not all manufacturer devices or every intermittent-network
failure. Recurring/unparseable server conflicts retain drafts and disallow forced
overwrite as documented. No version bump, Play publication, GitHub push or main
merge was performed during testing.
