# Theme surface consistency design

## Goal

Extend the existing white, black, and image themes from the main calendar to the in-app surfaces that currently break the visual flow: Settings, event details, and the date picker. The home-screen widget remains intentionally independent and uses its existing light-first presentation unless the user has enabled its separate theme option.

## Scope

The work changes appearance only. It does not change theme storage, image import, widget data, calendar synchronization, Premium behavior, or language selection.

Affected in-app surfaces:

- Settings sheet, including its header, grouped cards, rows, controls, and bottom-safe-area treatment.
- Event-detail sheet, including the scrim, sheet container, title, metadata, reminder rows, and close control.
- Date picker sheet, including its header, month navigation, weekday labels, selected/today states, actions, and scrim.
- Shared temporary feedback such as saved-settings messages and confirmation dialogs shown from these surfaces.

## Theme rules

### White

White remains the current quiet default: white/near-white surfaces, dark text, existing calendar accent colors, and clear dividers. No wallpaper or decorative overlay is added.

### Black

Black uses the existing dark palette: a near-black backdrop, opaque dark modal sheets, light primary text, muted secondary text, and high-contrast accent controls. The settings sheet must no longer appear as a bright white interruption above the dark calendar.

### Image

The selected wallpaper stays behind the calendar only. Settings, event details, date picker, confirmation dialogs, and feedback surfaces use a readable dark translucent surface over a darkened scrim; they do not repeat or crop the wallpaper inside each card. This keeps text stable on all user-provided images.

## Component behavior

A shared `CalendarThemePresentation` remains the single resolver. It will gain surface colors needed by settings and picker components, rather than having each screen infer black/image colors independently.

Each affected composable receives the resolved presentation (or a small immutable surface-color projection) from `AppRoot`. Components must use it for their container, primary/secondary text, outline/divider, control tint, and modal scrim. Existing localized strings and behavior remain unchanged.

The theme selection controls in Settings remain immediately usable. Selecting a new theme updates the visible Settings surface in place, without closing the sheet or restarting the activity.

The independent widget setting is unchanged: when “apply theme to widget” is off, it remains light; when it is on, the existing widget renderer decides its own supported appearance.

## Accessibility and interaction

All text/control pairings must meet readable contrast in black and image modes. Selected, disabled, error, focused, and pressed states remain distinguishable without relying solely on wallpaper color. System navigation bars retain a matching dark/light appearance where already supported, and touch targets/layout sizes are not changed.

## Error handling

If an imported wallpaper is unavailable, the existing resolver falls back to white. Every affected surface must follow that same resolved mode, so there is no mixed-theme state. Dialogs and messages inherit the current surface palette even while a settings save operation is in progress.

## Testing

- Unit tests cover the added presentation values for white, black, image, and missing-image fallback.
- Compose tests verify Settings, event details, and date picker receive the resolved presentation in each mode.
- Visual emulator checks cover white, black, and image themes, including opening/closing each sheet and changing theme while Settings is open.
- Regression checks confirm the widget remains light when its apply-theme setting is disabled.

## Deferred scope

No new wallpaper modes, dynamic Material You color extraction, redesigned widget skins, animation changes, or feature behavior changes are included in this phase.
