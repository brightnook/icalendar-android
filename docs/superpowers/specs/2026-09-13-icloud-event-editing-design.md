# iCloud Event Editing Design

## Scope

Add Premium-only creation, editing, and deletion of ordinary, non-recurring iCloud events. The existing calendar, synchronization, reminders, widget, and free-tier behavior remain available and unchanged.

## Entry points and interaction

- The selected day agenda header gains an add button. A new event defaults to the selected day, including dates opened from a widget.
- Swiping an agenda row left reveals Edit and Delete. Event details also expose Edit and Delete as an accessible fallback.
- Free users opening any write action see the existing Premium upgrade flow.
- Recurring events are read-only in this release and show a localized unsupported message.

## Editor

The first editor contains title, location, all-day, start, end, calendar, reminders, and notes. It does not expose repeat, travel time, invitees, or snooze. Title is required, end must be after start, and the selected calendar must be writable.

Reminder choices are none, at start, 5/15/30 minutes, 1/2 hours, 1/2 days, and a custom time. Premium users may add multiple reminders. Reminder definitions are written to iCloud and the local Android reminder schedule is reconciled after a successful write.

## Network and consistency

- Save and delete require a signed-in, online session; there is no offline write queue.
- Create uses a new UID and resource href. Update and delete use the stored resource href and ETag with conditional requests.
- A precondition failure is presented as a conflict. The app does not merge automatically and keeps the unsaved draft.
- Only after iCloud accepts the operation does the app update local Room data, refresh widgets, and reschedule reminders.
- The database schema prepared in version 23 is reused without another schema change.

## Release boundary

This work stays on `feature/icloud-event-editing`. It is not merged into the stable release branch or published until separately approved and tested.
