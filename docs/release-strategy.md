# Release and feature development strategy

Approved: 2026-09-12.

- Keep `feature/icloud-reminders` as the existing stable release-maintenance branch. Its historical name is retained intentionally.
- Baseline: source version 1.0.21 (versionCode 22). Production publication remains subject to closed-test acceptance and Google Play production access; a source baseline is not proof of publication.
- Only release preparation and bug fixes belong on the maintenance branch. Tag each verified published release against its exact source commit.
- Develop additions on `feature/icloud-event-editing`, starting from the same baseline. Carry maintenance fixes forward into this branch.
- Release the current stable feature set first. Deliver event creation and editing in a later update after internal testing, including upgrade and data-preservation checks.
- Premium will include ad removal, multiple reminders, and event creation/editing. Existing Premium purchasers receive these additions through the existing entitlement without another purchase.
- Initial write scope: online creation and editing of ordinary non-recurring events in writable iCloud calendars; title, time, all-day, location, notes, and reminders.
- Preserve unedited iCalendar properties and existing cloud reminders. Use conditional writes to detect conflicts, reconcile uncertain save outcomes before retrying, and coordinate writes with background sync.
- After a confirmed save, refresh local calendar views, widgets, and scheduled notifications.
- Recurrence editing, invitations, cross-calendar moves, and offline queued writes require later design.
- Update public product descriptions when the functionality is available. Do not advertise planned editing as currently shipped.

Next: detail the editor flow, permission discovery, conflict handling, and write verification before implementation.
