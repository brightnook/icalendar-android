# Brightnook Monetization and Website Design

**Status:** Approved for planning on 2026-08-25  
**Scope:** iOSCalendar advertising, one-time ad removal, release branches, and the Brightnook public website.

## Goals

- Keep iOSCalendar practical and unobtrusive while recovering a small portion of publishing costs.
- Offer a permanent, one-time removal of advertising for ¥250 in Japan and US$1.49 in USD-priced regions.
- Keep credentials and calendar data separate from advertising and billing.
- Publish a small, multilingual Brightnook developer website for product information, privacy policy, support, and AdMob verification.

## Product and Release Model

Google Play will distribute one free iOSCalendar application under the existing application ID, `com.brightnook.nookcalendar`.

- The `ad-supported` branch contains the public Google Play build, Google Mobile Ads integration, and Google Play Billing integration.
- The `ad-free` branch excludes the Google Mobile Ads SDK. It is a clean source variant for internal use or a future non-Play distribution channel.
- The existing `codex/icloud-calendar-android-dev` branch remains the day-to-day integration branch until release branches are created.
- The Google Play product is a non-consumable, one-time entitlement named `remove_ads_forever`. A purchase removes advertising permanently and can be restored after reinstalling or changing devices through the same Google Play account.

The `ad-free` branch is not a second Google Play listing. A second Play listing would require a separate application ID and would not provide the selected in-app upgrade path.

## Advertising Experience

The free Play build displays one anchored adaptive AdMob banner at the bottom of the main app screen.

- The banner has a reserved layout area and must not cover the calendar, event list, or bottom action controls.
- No advertising appears in the login screen, settings screen, event-detail dialog, date picker, home-screen widget, widget configuration screen, or notification surfaces.
- An active `remove_ads_forever` entitlement prevents SDK ad requests and hides the reserved banner area.
- Debug and automated test builds use Google's test advertising configuration only. Production ad identifiers are not used during development or tests.
- Advertising failures are non-fatal: the main app remains usable and the banner area collapses when an ad cannot be loaded.

The first release requests non-personalized advertising. Where legally required, Google's consent-management flow is shown before ad requests and lets the user review their privacy choices.

## Billing Experience

Settings receives an **Advertising and support** section with three states:

1. **Free version:** show the localized permanent-removal price and a purchase action.
2. **Purchase in progress or unavailable:** show an inline progress or retry message without blocking settings.
3. **Ads removed:** show the permanent-removal status and a restore-purchases action.

Google Play Billing is the source of truth. The app queries active purchases when the app starts, returns to the foreground, and opens the purchase area. A local persisted entitlement is only a short-lived display cache; it cannot grant permanent removal without a confirmed Play purchase. Completed purchases are acknowledged according to the Play Billing requirement.

## Privacy and Data Boundaries

- Apple Account identifiers, Apple app-specific passwords, calendar data, optional theme images, and calendar preferences are never supplied to AdMob or Google Play Billing for ad targeting.
- AdMob receives only the data its SDK requires to request and display an advertisement, subject to the consent choice and Google policy.
- Google Play Billing receives the product and purchase information needed to sell and restore `remove_ads_forever`.
- The current app's direct device-to-iCloud calendar synchronization remains read-only and unchanged.
- The in-app privacy policy, repository policy, Brightnook website policy, and Play Console Data safety disclosure must be updated before a public release to describe AdMob, consent handling, and Play Billing accurately.

## Brightnook Public Website

Brightnook is the public developer brand. The intended GitHub Organization and GitHub Pages host are `brightnook` and `https://brightnook.github.io/`, subject to availability when they are created.

The website is a static GitHub Pages site with no analytics, cookies, or database.

### Information architecture

- Home page: Brightnook studio introduction and product cards.
- iOSCalendar product page: product purpose, feature overview, app-store link after publication, support email, and privacy-policy link.
- Privacy-policy page: an accurate public copy of the app policy.
- `app-ads.txt`: served at `https://brightnook.github.io/app-ads.txt` after the AdMob publisher identifier is available.

Each public page is available in Chinese, English, and Japanese through separate localized paths and provides a visible language switcher. The home page uses browser-language detection only to select an initial language; it does not collect analytics or persist tracking data.

## Verification

Before release, verify all of the following:

- Test advertisement display, load failure, network loss, and post-purchase removal.
- Purchase, cancellation, pending-purchase handling, acknowledgement, restore purchase, reinstall, and device-change behavior.
- No ad rendering in excluded screens and all home-screen widget sizes.
- Chinese, English, and Japanese strings in the app and website.
- Light, dark, and image-background themes with the banner's reserved/collapsed space.
- Existing iCloud sign-in, manual sync, hourly/daily/disabled background sync, logout, cache clearing, and widget behavior.
- AdMob account approval, application verification, `app-ads.txt` verification, Play Console content declarations, Data safety form, and closed-track testing.

## Non-goals for the First Monetization Release

- Interstitial, rewarded, native, or widget advertising.
- Subscription billing.
- Personalized advertising as a product feature.
- A separate paid Google Play application.
- Any change to iCloud calendar write support, event editing, recurring-event editing, or reminders.
