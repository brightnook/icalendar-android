# iCloud Event Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe Premium-only creation, editing, and deletion of ordinary iCloud calendar events.

**Architecture:** Extend the authenticated CalDAV gateway with conditional resource writes, then coordinate remote success with parsing and Room replacement through a focused event-editing repository. Add a state-driven Compose editor and agenda actions on top, reusing the version 23 resource table and existing billing, widget, and reminder services.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, OkHttp, iCal4j, Room, coroutines, JUnit, Robolectric, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-09-13-icloud-event-editing-design.md`

## Global Constraints

- Work only on `feature/icloud-event-editing`; do not change the stable release branch.
- Support only ordinary, non-recurring events in this release.
- All writes require Premium, login, and online CalDAV success.
- Preserve the draft after network, validation, authentication, or conflict failure.
- Reuse Room schema version 8 from app version 23; do not change the database version.
- Do not add repeat, travel time, invitees, offline writes, merge, or snooze.

---

### Task 1: Conditional CalDAV resource writes

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/caldav/CalDavGateway.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/caldav/ICloudCalDavGateway.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/caldav/CalDavFailure.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/caldav/ICloudCalDavGatewayTest.kt`

**Interfaces:**
- Produces: `createResource(calendar, href, calendarData): RemoteResource`, `updateResource(calendar, resource): RemoteResource`, and `deleteResource(calendar, href, etag)`.
- Produces: `CalDavFailure.Conflict` for HTTP 409/412.

- [x] **Step 1: Write failing gateway tests** for PUT with `If-None-Match: *`, PUT with `If-Match`, DELETE with `If-Match`, returned ETag handling, and 409/412 conflict mapping using MockWebServer.
- [x] **Step 2: Run** `.\gradlew.bat :app:testDebugUnitTest --tests "com.brightnook.nookcalendar.caldav.ICloudCalDavGatewayTest" --no-daemon --console=plain`; expect failures because write methods and conflict mapping do not exist.
- [x] **Step 3: Implement the minimal API** with trusted URL resolution, `text/calendar; charset=utf-8`, existing size validation, conditional headers, bounded response handling, and no redirect following.
- [x] **Step 4: Re-run the focused tests** and expect PASS.
- [x] **Step 5: Commit** `feat: add conditional CalDAV event writes`.

### Task 2: ICS serialization and editable-event policy

**Files:**
- Create: `app/src/main/java/com/brightnook/nookcalendar/caldav/IcsEventWriter.kt`
- Create: `app/src/main/java/com/brightnook/nookcalendar/domain/EventEditModels.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/caldav/IcsEventWriterTest.kt`

**Interfaces:**
- Consumes: editor title/location/time/all-day/reminder/notes values.
- Produces: `EventDraft`, `EventWriteTarget`, `EventWriteValidation`, and `IcsEventWriter.write(draft, uid, now): String`.

- [x] **Step 1: Write failing tests** covering escaped text, timed and all-day DTSTART/DTEND, UID/DTSTAMP, multiple DISPLAY VALARMs, notes/location, and rejection of empty title or invalid ranges.
- [x] **Step 2: Run** the writer test class and verify it fails because the types do not exist.
- [x] **Step 3: Implement deterministic VCALENDAR/VEVENT serialization** with CRLF lines and folded UTF-8-safe content, retaining only the approved fields.
- [x] **Step 4: Run the writer tests** and expect PASS.
- [x] **Step 5: Commit** `feat: serialize editable iCloud events`.

### Task 3: Remote-first event editing repository

**Completed prerequisite:** writable CalDAV privileges, raw ICS, and ETags are now carried through initial and incremental synchronization into the existing schema-8 tables. This closes the version-23 data-population gap without another migration.

**Files:**
- Create: `app/src/main/java/com/brightnook/nookcalendar/domain/EventEditingRepository.kt`
- Create: `app/src/main/java/com/brightnook/nookcalendar/data/RoomEventEditingRepository.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/data/CalendarDao.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/data/EventDao.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/AppContainer.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/data/RoomEventEditingRepositoryTest.kt`

**Interfaces:**
- Consumes: conditional gateway methods, `IcsEventWriter`, version 23 `calendar_resources`, parser, DAOs, widget updater, and reminder scheduler.
- Produces: calendar choices plus `create`, `update`, and `delete` results with typed validation/auth/network/conflict failures.

- [ ] **Step 1: Write failing repository tests** proving remote-first ordering, no local mutation on failure, ETag refresh, local row replacement/deletion after success, recurring-event rejection, widget refresh, and reminder reconciliation.
- [ ] **Step 2: Run the repository test class** and verify the missing repository fails compilation.
- [ ] **Step 3: Add only the DAO queries needed** to load writable calendars/resources and atomically replace or remove one resource's parsed rows.
- [ ] **Step 4: Implement the repository** so Room changes occur only inside a transaction after remote success.
- [ ] **Step 5: Run repository plus migration tests** and expect PASS without changing schema version 8.
- [ ] **Step 6: Commit** `feat: coordinate iCloud event edits`.

### Task 4: Premium editor state and validation

**Files:**
- Create: `app/src/main/java/com/brightnook/nookcalendar/ui/eventedit/EventEditorViewModel.kt`
- Create: `app/src/main/java/com/brightnook/nookcalendar/ui/eventedit/EventEditorState.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/eventedit/EventEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `EventEditingRepository`, selected date, optional event instance ID, login state, and purchase state.
- Produces: stable editor fields, saving/deleting flags, field errors, retained draft, and one-shot success/failure effects.

- [ ] **Step 1: Write failing state tests** for selected-date defaults, edit loading, Premium/login gates, validation, duplicate-submit blocking, conflict handling, and retained drafts.
- [ ] **Step 2: Run the focused test class** and verify failure.
- [ ] **Step 3: Implement immutable state transitions** and submit/delete orchestration without navigation side effects in the repository.
- [ ] **Step 4: Run focused tests** and expect PASS.
- [ ] **Step 5: Commit** `feat: add event editor state`.

### Task 5: Editor screen and localized copy

**Files:**
- Create: `app/src/main/java/com/brightnook/nookcalendar/ui/eventedit/EventEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Test: `app/src/androidTest/java/com/brightnook/nookcalendar/ui/eventedit/EventEditorScreenTest.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/data/AppLanguageResourceTest.kt`

**Interfaces:**
- Consumes: `EventEditorState` and callbacks from Task 4.
- Produces: accessible create/edit form with title, location, all-day, start/end, calendar, multiple reminders, notes, cancel, save, and delete confirmation.

- [ ] **Step 1: Write failing Compose tests** for field visibility, selected-date defaults, all-day behavior, multiple reminders, saving disabled state, localized errors, and draft retention.
- [ ] **Step 2: Run focused unit/instrumentation tests** and verify failure.
- [ ] **Step 3: Implement the modal editor** using current theme surfaces, scrollable content, localized date/time formatting, and no unsupported fields.
- [ ] **Step 4: Add complete English, Simplified Chinese, and Japanese resources** and pass the locale resource parity test.
- [ ] **Step 5: Run focused tests** and expect PASS.
- [ ] **Step 6: Commit** `feat: add localized event editor`.

### Task 6: Agenda entry points, swipe actions, and detail fallback

**Files:**
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/main/AgendaList.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/main/EventDetailSheet.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/main/MainCalendarScreen.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/main/MainCalendarViewModel.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt`
- Test: `app/src/androidTest/java/com/brightnook/nookcalendar/ui/main/MainCalendarScreenTest.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/main/MainCalendarViewModelTest.kt`

**Interfaces:**
- Consumes: editor screen/state and existing Premium upgrade action.
- Produces: agenda-header add, left-swipe edit/delete, detail fallback actions, recurring-event message, and post-success refresh while preserving selected date.

- [ ] **Step 1: Write failing UI tests** for add defaults, Premium gating, swipe reveal, detail fallback, recurring read-only behavior, and selected-date preservation after success.
- [ ] **Step 2: Run focused tests** and verify failure.
- [ ] **Step 3: Add the agenda-header action and dismissible row actions** with explicit accessibility labels and confirmation before delete.
- [ ] **Step 4: Route editor results through AppRoot** and invoke the existing upgrade UI for free users.
- [ ] **Step 5: Run focused tests** and expect PASS.
- [ ] **Step 6: Commit** `feat: expose calendar event editing`.

### Task 7: Regression and device verification

**Files:**
- Modify: `docs/release-strategy.md`

**Interfaces:**
- Consumes: all earlier tasks.
- Produces: a tested feature branch ready for user review, without publishing it.

- [ ] **Step 1: Run** `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:bundleDebug --no-daemon --console=plain` and require all tasks to pass.
- [ ] **Step 2: Install the debug build on the connected emulator/device** and verify create, edit, delete, conflict/error retention, reminders, sync, widgets, themes, and all three languages.
- [ ] **Step 3: Confirm database version remains 8** and an upgrade from version 23 starts normally with existing data.
- [ ] **Step 4: Update release strategy notes** with tested scope and remaining exclusions.
- [ ] **Step 5: Commit** `docs: record event editing verification`.
