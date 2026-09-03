# iCloud Calendar Reminders Implementation Plan

> **For Codex:** Execute this plan directly in the `feature/icloud-reminders` worktree. Keep the existing `ad-supported` checkout unchanged.

## Goal

Deliver local Android notifications for the `VALARM` definitions included with iCloud Calendar events. A notification opens NookCalendar on the event date and reuses the existing event-detail sheet. The app does not create or edit reminders in iCloud.

## Scope and behavior

- Parse `VALARM` components with `ACTION:DISPLAY`, including absolute triggers and negative/positive duration triggers relative to `DTSTART` or `DTEND`.
- Preserve reminder definitions on a calendar event and expand them to the already persisted event instances. This gives recurring events one reminder per occurrence.
- Schedule future timed and all-day instances. Unsupported or invalid `VALARM` definitions are ignored safely.
- Reconcile alarms after every successful snapshot or delta application: cancel alarms no longer present, cancel/reschedule moved alarms, and retain stable IDs for unchanged alarms.
- Use `AlarmManager` with an exact-and-idle alarm when the user/device grants exact-alarm access; otherwise use an allow-while-idle inexact alarm. Android 13+ notification permission is requested before notifications can be shown.
- Use one high-importance notification channel. No full-screen or Live Update behavior. Tapping the notification launches `MainActivity`, selects the event date, and opens the existing detail sheet.
- Recreate all upcoming alarms after device reboot, app upgrade, timezone change, and package replacement.

## Implementation steps

1. **Model and iCalendar parsing**
   - Add a `ReminderSpec` domain value and carry it from `ParsedEvent` to `CalendarEvent`.
   - In `Ical4jIcsParser.sourceEvent`, enumerate `VALARM` children, retain only display alarms, and convert trigger values to a serializable model.
   - Add parser tests for a negative `DTSTART` trigger, an absolute trigger, a `DTEND` trigger, and ignored unsupported alarms.

2. **Persistence and reminder planning**
   - Add a Room table keyed by deterministic reminder ID, with instance ID, trigger epoch, event title/location and calendar colour.
   - Add migration 4-to-5 and schema export; migrate in `AppContainer`.
   - Extend repository snapshot/delta processing so persisted instances are converted to planned future reminders in the same database transaction, including removal of stale reminders.
   - Add repository/migration tests for unchanged, moved, deleted, and recurring event reminders.

3. **Android scheduler and notification receiver**
   - Add a small platform-facing reminder scheduler abstraction backed by `AlarmManager` and a broadcast receiver.
   - Make the scheduler diff the persisted reminder plan against currently scheduled pending intents; use deterministic request codes and immutable intents.
   - Create the notification channel and post an event notification containing a content intent built with the existing `CalendarLaunchTargetDelivery` extras.
   - Add manifest entries for notification permission, exact-alarm capability, receiver, boot/timezone/package replacement actions.
   - Add unit/Robolectric tests for the trigger selection and content intent target.

4. **App lifecycle integration and permission UX**
   - Initialise notification channel and schedule reconciliation in `CalendarApplication`/`AppContainer`.
   - Ask for Android 13+ notification permission in the app flow only when upcoming reminders exist; if denied, keep syncing reminder definitions and show a clear status in settings.
   - Reconcile after snapshot/delta sync, cache clearing, logout, reboot and timezone change.
   - Add localization for reminder status, notifications and permission explanation in English, Simplified Chinese and Japanese.

5. **Verification**
   - Run parser, repository/migration, scheduler and routing tests.
   - Build a debug APK, install on an emulator/device, use a short iCloud alarm lead time and verify: notification arrival, notification tap opening the correct detail sheet, reschedule after changing iCloud time, and cancellation after deleting the event.

## Acceptance criteria

- iCloud `DISPLAY` alarms appear as Android notifications after a sync.
- Existing iCloud reminders are never modified by the app.
- Updating or deleting an iCloud event after a sync update cannot leave an old alert scheduled.
- Notification tap opens the main calendar at the matching date with its existing detail view visible.
- A missing Android notification permission never crashes sync or the app.
