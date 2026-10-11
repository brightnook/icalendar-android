# Remove Ads Purchase Flow Design

## Goal

Let an authenticated user permanently remove the banner advertisement through the Google Play one-time product `remove_ads_forever`, and restore that entitlement after reinstalling or changing devices.

## Scope

- The ad-supported application only.
- One product: `remove_ads_forever` (`INAPP`), shown in the settings screen.
- Google Play's own purchase UI handles payment method and confirmation.
- No subscriptions, consumables, server-side account system, or custom payment method.

## User experience

The settings screen has a separate Support section.

- Available: show **Remove ads forever** with the Play-provided local price. Tapping it opens the Google Play purchase UI.
- Loading: disable both actions and show a progress label.
- Pending: show **Payment pending** and keep advertisements visible.
- Owned: show **Ads removed**; neither purchase nor restore is needed.
- Unavailable: explain that Google Play billing is temporarily unavailable; the restore action remains available after a new query succeeds.
- Restore purchases: re-query Google Play and update the same state. This handles reinstallation and a new device signed in to the owning Google account.
- Cancellation or billing errors: return to the latest known non-owned state and show a short error message. The app never presents a fake success state.

All copy is supplied in Chinese, English, and Japanese resource files. The price always comes from Play Billing rather than being hard-coded.

## Architecture

`BillingGateway` is the boundary around Play Billing. It adds `purchase(activity)` alongside refresh and restore. The concrete gateway owns the `PurchasesUpdatedListener`, queries the one-time product detail, calls `launchBillingFlow`, then turns the asynchronous callback into a purchase outcome. The gateway acknowledges only completed purchases and treats pending purchases as not entitled.

`MonetizationRepository` stays the single source of truth for `MonetizationUiState`. It starts the purchase, exposes loading state while the Play UI is active, then maps the completed, pending, cancelled, or failed outcome to a safe UI state. Only a confirmed completed entitlement makes `canShowAds` false.

`AppRoot` passes an Android `Activity` only at the UI boundary. The settings composable displays the state and invokes callbacks. `SettingsViewModel` is not made responsible for Play Billing because it must remain Android-UI-independent and is retained for calendar preferences.

## Data flow

1. AppRoot refreshes the repository after authentication.
2. The user taps Remove ads forever in SettingsScreen.
3. AppRoot asks MonetizationRepository to purchase using the current Activity.
4. The repository marks its state loading; the gateway opens Google Play's UI.
5. The Play callback produces one of completed, pending, cancelled, or failed.
6. The repository maps that result to `MonetizationUiState`; a completed purchase hides the banner immediately.
7. Restore purchases runs the existing purchase query and uses the same mapping.

## Failure handling

- The product is absent or cannot be queried: no purchase UI opens; state is unavailable and ads remain visible.
- Google Play rejects the launch or reports an error: no entitlement is granted; state becomes available when a price is known, otherwise unavailable.
- User cancellation: no entitlement is granted; retain the known price and allow another attempt.
- Pending payment: state is pending and ads remain visible until a later refresh or restore reports completion.
- Activity recreation or coroutine cancellation: the repository never changes entitlement unless a completed purchase was returned; the next app start refreshes Play ownership.

## Testing

- Unit-test repository mappings for completed, pending, cancelled, and failed purchase outcomes.
- Unit-test the Play Billing mapper for listener result conversion and acknowledgement eligibility.
- Compose-test settings actions for available, loading, owned, and pending states without depending on Google Play.
- Build a debug APK and perform a Google Play license-test purchase and restore on a physical device before release.

## Constraints

- Product ID is exactly `remove_ads_forever`.
- Price is Google Play-provided, not hard-coded in the UI.
- Advertisements remain visible for unavailable, cancelled, failed, and pending states.
- The banner remains confined to the authenticated main calendar screen.
- No release build is created until a real `adMobAppId` and Google Play product are configured.
