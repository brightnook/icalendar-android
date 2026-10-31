# Local-first event saving

Approved direction: persist new/edited events locally, upload once in background,
retain failures and retry only when the user requests synchronization. Delete
remains online and confirmed. Work stays on fix/play-quality-actions; no release,
main merge, SDK migration or version bump in this task.

## Architecture and alternatives

Waiting for iCloud then refreshing asynchronously does not provide the offline
save guarantee requested. Use a durable Room outbox instead of in-memory tasks.
Overlay pending drafts on remote calendar reads so remote refreshes cannot erase
local changes; retain original resource/UID/ETag as the optimistic concurrency base.
Room schema 8 must migrate without losing cached events or settings.

## Behavior

- Save returns once the local transaction commits, not after network calls.
- Pending items show pending/sending/failed/conflict state in list and detail.
- Background upload attempts pending items once; failed items wait for manual sync.
- Process death, app restart and ambiguous network responses retain durable work.
- Stable UID and conditional PUT prevent duplicates. Read back before uncertain
  retries; never overwrite changed remote data automatically.
- Preserve local edits on conflict. Display remote vs local information and require
  an explicit user decision to keep local with a fresh precondition or use remote.
- Cross-calendar transfer uses conditional server MOVE with Overwrite:F, not
  create-then-delete. Persist intermediate progress to avoid replaying PUT/MOVE.
- Preserve pending overlay until remote refresh has reconciled server success.
- New edits while a write is in flight must not race or discard newer drafts.
- Queue records are account-bound. Sign-out/cache clear must not leak queued data
  into another account; warn before discarding unsynced data in destructive flows.
- Do not log event text, credentials or account identifiers.
- EN/zh-CN/ja parity. Do not claim successful device/cloud verification from mocks.

## Interfaces

Domain enum PendingEventSyncState: PENDING, SYNCING, FAILED, CONFLICT.
EventInstance and EventDetail gain pendingSyncState (nullable, default null).
EventDetail gains remoteConflict: EventDraft? (default null) for conflict comparison.
EventEditingRepository gains resolveConflict(instanceId:String, keepLocal:Boolean)
returning EventEditingResult, with a default Invalid implementation for old fakes.
SyncCoordinator gains syncManually(request:SyncRequest):SyncResult defaulting to sync.
Only explicit user refresh calls syncManually; background/month requests do not
retry failed/conflicted work. A worker restores pending work across process death.

Gateway adds getResource(calendar,href):RemoteResource? (404 = null) and conditional
moveResource(calendar,resource,destination):RemoteResource. No redirect or cross-origin
credential forwarding. Noncollection MOVE only accepts 201/204, not generic 207.

## Acceptance

Tests cover durable save with zero network requests, schema 8 upgrade, remote
refresh protection, manual retry, crash recovery, unknown successful PUT/MOVE,
ETag conflicts, missing/readonly destination, no overwrite, account changes,
in-flight edits, and deletion remaining online. Build and local tests first,
then connected-device visual/upgrade checks and disposable test-account cloud
round trip if a signed-in emulator is available.
