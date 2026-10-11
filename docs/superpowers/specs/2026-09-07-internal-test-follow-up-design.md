# NookCalendar internal-test follow-up design

Date: 2026-09-07

## Scope split

This work is deliberately split into two releases.

### Next release: functional fixes

1. Fix Japanese app-language rendering in release builds.
2. Keep the last known Google Play price visible while purchase or restore runs.
3. Display the price returned by Google Play for the signed-in account's storefront; display language does not determine currency.
4. Use the launcher name `NookCalendar` consistently.
5. Refresh every existing home-screen widget after a successful manual or scheduled sync.
6. Increase touch separation around the 30-day widget range option.
7. Allow users to reconfigure an existing widget reliably.

### Later release: Settings theme coverage

Extend the selected light, dark, or image theme to the Settings screen. This includes the page background, translucent section cards, controls, dialogs, and bottom actions. It is visual scope and is intentionally excluded from the functional-fixes release.

## Localized release behaviour

The app retains the custom Compose localized-context approach so changing a language does not recreate the Activity or cause the earlier black-screen/flicker issue. The release build will be installed directly on an emulator before publication and tested for English, Chinese, and Japanese resource rendering. The test must use the signed/release variant because debug-only success is insufficient evidence for internal testing.

## Google Play price behaviour

`ProductDetails` remains the source of price truth. Settings retains the last successfully received formatted price while an asynchronous purchase or restore request executes; it does not replace the price text with a loading sentence. A background indicator may be used only if it does not change the layout.

No currency conversion or language-based price mapping is added. Google Play returns a localized formatted price according to the account's Play storefront and the active product offer. Language changes can refresh UI resources but must not fabricate a new currency.

## Widget refresh and reconfiguration

Successful sync calls the widget refresh notifier for all active widget IDs. The refresh path updates the parent RemoteViews and invalidates the collection view so a widget that was created before calendar data existed can render newly synchronized events without removal/re-addition.

The existing Android widget configuration Activity remains declared through `android:configure`, allowing launchers that expose their standard Configure action to use it. To ensure a reliable cross-launcher path, the app's Settings screen gains a Widget management entry. It lists active widget IDs with their current range and opens the same configuration Activity for the selected ID. Saving re-renders only that widget.

## UI refinements

The 30-day range control gains additional visual and touch separation from adjacent options while preserving accessible target sizing. The application label is updated in all localized app-name resources to `NookCalendar`.

## Error handling

- If no widgets are active, Widget management shows an empty-state explanation instead of a non-functional action.
- If a widget ID disappears while its configuration screen is open, saving exits safely without updating another widget.
- If Play price lookup fails, retain the last known price; show the existing unavailable message only when no prior price exists.

## Verification

1. Add/adjust unit tests for retained price state, widget refresh invalidation, and widget configuration management.
2. Build both debug and signed release variants.
3. Directly install the release APK on the emulator and verify all three language choices.
4. Create a widget before syncing, sync events, and verify the existing widget updates without being removed.
5. Reconfigure an existing widget through the app entry and verify range/calendar changes take effect.
