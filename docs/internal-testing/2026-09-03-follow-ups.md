# Internal testing follow-ups — 2026-09-03

Status: recorded for the next development session. No functional changes or release upload were made for this list.

## Release behaviour to investigate

1. **Japanese language selection differs by install source**
   - Emulator build: switching to Japanese works.
   - Internal-testing build 1.0.12 (13): the language selector changes to Japanese, but the interface remains English.
   - Confirm the installed artifact/version and compare release-minified resource and locale handling with the debug build.

2. **Apply themes to the Settings screen**
   - The Settings screen currently does not inherit the selected app theme.
   - Treat as a scope extension for the next version; define which theme elements apply there (background image, dark/light surfaces, controls, dialogs, and bottom area).

3. **Keep the Play price label stable during purchase / restore**
   - Tapping “Upgrade to Premium” or “Restore purchases” temporarily replaces the price label with “Checking Google Play”.
   - Keep the last known price visible while the background request runs; use a non-layout-shifting progress affordance only if needed.

4. **Show Play price in the user’s storefront currency**
   - The English interface still shows a yen price.
   - Confirm that `ProductDetails` localized pricing is fetched after a language change / Store locale change, and do not derive currency from display language alone.
   - Expected behaviour should follow the Google Play account/storefront currency, not necessarily the app language.

5. **Display name capitalization**
   - Change the launcher/app name from “Nookcalendar” to “NookCalendar”.
   - Review all localized app-name resources and Play listing name for consistent capitalization.

## Home-screen widget follow-ups

6. **Refresh existing widgets after calendar sync**
   - After the app synchronizes events, an existing widget can continue to show “No upcoming events”.
   - Repeated removal and re-adding eventually displays the events.
   - Verify that successful manual and scheduled sync both notify all active widget IDs and invalidate configuration-derived calendar selections when calendars first become available.

7. **Increase spacing around the 30-day widget option**
   - In the widget configuration UI, the 30-day option is too close to neighboring controls and can be tapped accidentally.
   - Increase touch-target separation without reducing accessibility target sizes.

8. **Provide a way to edit an existing widget configuration**
   - Users need to change the widget’s display range after it has been placed on the home screen.
   - First investigate whether `AppWidgetProviderInfo.configure` / Android launcher “Configure” menu is available for the current widget implementation.
   - If launchers do not consistently expose it, add an in-app widget-management entry that lists configured widgets and opens configuration for a selected widget.

## Suggested implementation order

1. Reproduce and fix item 1 with a release-equivalent emulator build.
2. Fix widget data refresh and reconfiguration (items 6 and 8), then verify on an existing home-screen widget.
3. Make the purchase UI stable and correct localized-price behaviour (items 3 and 4).
4. Apply the small UI/name refinements (items 5 and 7).
5. Design and implement Settings-screen theme coverage (item 2) as the next visual scope.
