# Theme background design

## Goal

Add a first-version theme system for the main calendar screen. Users can select a white theme, a black theme, or a locally imported still or animated image. A separate setting can apply the selected theme to the home-screen agenda widget. Settings screens remain light and unchanged. Local video backgrounds are intentionally out of scope.

## User experience

The settings screen gains a Theme section with three background choices:

- White theme
- Black theme
- Image / animated image

Image selection uses the Android system photo picker. Images are displayed like a phone wallpaper: fill the available calendar screen and crop from the center as needed. Animated GIF and animated WebP backgrounds play only in the main calendar screen.

The Theme section includes an Apply to widget switch. It is off by default. When enabled, a widget uses the current white or black theme, or a static first frame for an image or animation. The widget continues to render readable event content over a translucent light surface.

## Data model and storage

`AppSettings` gains a background mode, a local background asset key, and an apply-to-widget flag. Values live in the existing DataStore preferences and default to the current white appearance with the widget flag off.

`ThemeAssetStore` owns app-private files under the app files directory. Importing copies the selected picker URI to a newly named temporary file, validates it, then atomically replaces the active asset. The stored key is relative to that directory. Replacing an asset removes only the prior theme asset after the replacement is available. Choosing white or black retains an imported image; a separate Remove image action deletes it.

The importer accepts still images, GIF, and animated WebP. It rejects video, undecodable content, and files above the defined size limit. Failed imports do not alter current settings or delete a current asset.

## Rendering

`MainCalendarScreen` receives resolved theme state from settings. White uses the current appearance. Black uses a dark background with light calendar text and surfaces. Image modes place the imported wallpaper behind the calendar and agenda content, which retain a translucent surface for contrast. The wallpaper renderer handles centered crop and animation playback.

Weekend days in both month and year views use explicit contrast-aware colors: Saturday is blue and Sunday is red. The resolver supplies a stronger light-theme pair and brighter dark/image-theme pair; working days retain the normal text color.

The settings screen is deliberately outside this renderer, so it stays in the current light appearance.

## Widget behavior

`WidgetUpdater` resolves the widget theme only when Apply to widget is enabled. White and black use their matching color resources. An imported still or animation is decoded once to a downscaled bitmap; the first frame is stored as a widget-safe preview and applied as the widget background. RemoteViews never plays animation.

If preview generation fails, the widget uses the existing light background and remains fully usable. Disabling Apply to widget clears the widget preview reference and restores existing light widget colors.

## Errors and lifecycle

The UI reports an import error in the settings screen without changing the selected background. Missing imported assets after a restore or cleanup fall back to white and clear the stale image mode on the next successful settings update. Background decoding is bounded by the image size limit and target dimensions. Animation playback is only active while the main calendar composition is visible.

## Testing

Unit tests cover default settings, read/write round trips, theme selection, asset replacement, failed import preservation, image removal, weekend color contrast, and widget theme resolution. Image decoder tests cover first-frame extraction and invalid input. UI tests cover the Theme settings controls and main-screen routing of the selected theme. Widget tests cover white, black, image-preview, disabled, and preview-failure fallbacks.

## Deferred scope

Local video backgrounds, video preview extraction, video playback lifecycle, and animated home-screen widgets are deferred to a later phase.
