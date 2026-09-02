# Reminder Premium Boundary Design

## Purpose

NookCalendar reads reminder definitions from iCloud Calendar and delivers them as Android notifications. The free and premium editions need a simple, user-visible distinction without making recurring calendar events unreliable.

## Product rules

### Free edition

- Every supported timed or all-day event instance may have one local reminder.
- If an iCloud event contains multiple `ACTION:DISPLAY` alarms, use the earliest trigger instant for that event instance.
- The choice is calculated from trigger time, not from the order of `VALARM` entries in the iCalendar resource.
- iCloud calendar synchronisation, calendar browsing, widgets, and recurring-event notifications remain available.
- Advertising remains enabled unless the user has an existing premium purchase.

### Premium edition

- A one-time purchase removes advertising permanently.
- Every supported iCloud `ACTION:DISPLAY` alarm is delivered for an event instance.
- Recurring iCloud events continue to create reminders for every expanded occurrence.

### Deferred functionality

- A user-defined repeat alert (for example, alert again after 10 minutes) is not part of this release.
- It is a candidate for a later premium update because it is an Android-side feature, not a direct translation of a standard iCloud calendar reminder.
- No snooze, acknowledgement, or repeated-ringing UI is introduced now.

## Scheduling behavior

- The app calculates every reminder after a successful snapshot or incremental iCloud sync.
- The free/premium filter runs before Android alarms are registered, so an upgrade can schedule remaining iCloud alarms without another server change.
- Changes, cancellation, cache clearing, visibility changes, reboot, timezone changes, and app upgrades reconcile registered Android alarms.
- Notification taps open the existing main calendar screen, select the instance date, and display the existing event detail sheet.

## Edge cases

- Non-display alarms such as email or audio are ignored.
- If two reminders trigger at the same time, Android posts separate notifications; the system may visually group them.
- If no notification permission is granted, definitions and schedules remain locally reconcilable, but Android does not display the notification.
- An iCloud event with no display alarm creates no local reminder.

## Acceptance criteria

- Free users receive the earliest display reminder for each event instance, including repeated events.
- Premium users receive every display reminder configured in iCloud for each event instance and see no ads.
- Upgrading schedules additional upcoming iCloud reminders without duplicate alerts.
- No later premium “repeat alert” behavior is implied or implemented in this release.
