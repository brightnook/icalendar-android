# Event editing verification — 2026-09-18

Branch: `feature/icloud-event-editing`. App: 1.0.24 (25).
Production baseline 1.0.22 (23) remains unchanged.

## Verified

- 389 JVM tests, lintDebug and assembleDebug passed.
- Four focused device tests passed on Pixel_901 / Android 17: approved editor fields,
  left and right swipe reveal without removing the event, and fixed detail toolbar
  actions before and after scrolling long content at large font size.
- Opt-in `LiveEventEditingTest` passed against the user-authorized signed-in test
  account (106.804 seconds). It created one uniquely marked ordinary timed event,
  verified the resource by independently reading iCloud, edited title/location/end
  time and two reminder definitions, checked local synchronization, deleted the
  event, then checked cloud and local absence.
- Only disposable events with this test's UID/title/notes markers were cleaned up.
  Account credentials and existing event contents were not logged.

## Test execution notes

Install only the androidTest APK and run the instrumentation explicitly with
`liveTestAccount=true`; default test runs skip cloud writes. Do not use Gradle's
connected test install/cleanup workflow after the user signs in if retaining the
existing app installation is necessary.

Independent cloud verification uses a separate gateway. Authentication replaces
the gateway's active session, so retaining an old RemoteCalendar while app sync
reauthenticates on that same gateway makes the test's read-back invalid.

## Remaining

- The full legacy MainCalendarScreen device suite had 12 failures out of 28 total
  device tests before the focused rerun. One outdated detail-scroll expectation
  was corrected; the whole suite has not been rerun or declared green.
- One calendar's incremental request took roughly 30 seconds before range-query
  fallback during live verification. Investigate separately; successful CRUD is
  not evidence that synchronization latency is acceptable.
- This verifies repository/network consistency, not the entire manual UI path or
  purchase entitlement behavior. Real-device interactions, all-day writes,
  conflict/draft retention, and widget/notification behavior need device acceptance.
- The initial emulator baseline was version 9, not version 23. Exact version-23
  data-preserving upgrade acceptance remains unverified on this emulator.

## Incremental latency investigation

- Added a request-boundary test: collection REPORT uses an 8-second read timeout;
  authentication, multiget batches, range queries, PUT and DELETE keep 30 seconds.
  The test failed with all requests at 30 seconds before the change.
- Added debug-only count-free operation timing logs, without URLs, XML, account
  identifiers, tokens, credentials or event contents.
- Live CRUD after the timeout change passed in 109.312 seconds; the diagnostic
  repeat passed in 107.194 seconds. This is **not** a demonstrated speedup over
  the original 106.804-second run.
- A no-change refresh passed in 3.492 seconds.
- Changed-event diagnostics located the bottleneck more precisely:
  `sync_collection` completed in about 0.18 seconds; `calendar_multiget` waited
  30.012 / 30.004 seconds; fallback `calendar_query` took 1.258 / 1.305 seconds.
  The principal bottleneck is therefore changed-resource retrieval, not discovery
  of changes or an overly broad initial date window.
- Omitted Depth only on multiget, consistent with RFC 4791 section 7.9; ordinary
  range queries and collection synchronization retain `Depth: 1`. The regression
  test failed with expected null / actual 1 before the change. All 390 unit tests,
  lintDebug, and debug/test APK builds passed afterward.
- Live CRUD with the omitted header passed in 109.043 seconds, including cloud
  and local read-back and deletion of the disposable test event. Multiget still
  took 30.005 / 30.008 seconds; fallback query took 1.209 seconds. This change
  **does not resolve the latency**. Installed APKs with `-r`, preserving login.
- A shorter multiget timeout with existing range-query fallback is a possible
  next experiment, not implemented or verified in this iteration.
- No production branch changes, schema changes, publication or commit was made.
