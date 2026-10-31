# Bitmap memory investigation

Baseline: version 31 (1.0.30), main commit `3ee6e0a`.
Branch: `investigate/bitmap-memory`.

## Evidence

- Play Console's recommendation identifies version 23 (1.0.22), not version 31.
- The only production `BitmapFactory` call is in `WidgetUpdater.loadStaticPreview`.
  It decodes a cached PNG preview rather than the original wallpaper.
- `createWidgetThemePreview` uses `ImageDecoder.setTargetSize` during decoding,
  preserving aspect ratio within 384 x 576 pixels. It recycles the generated
  bitmap after writing the preview. A full ARGB pixel buffer at that maximum
  size is 884,736 bytes (0.844 MiB), excluding decoder and compressed-file overhead.
- Main-app wallpaper uses Coil `AsyncImage` with layout constraints.
- Image import has a 20 MiB encoded-file limit and samples validation images
  whose largest dimension exceeds 4096 pixels.

## Decision

Verification: reran `WidgetThemePreviewTest` (4 tests) and `ThemeAssetStoreTest`
(10 tests) with `--rerun-tasks`; all 14 passed, with zero failures or errors.
The preview test confirms an 800 x 400 source becomes a 200 x 100 PNG when
requested within 200 x 200 bounds. Animated preview and failure preservation
tests also passed. These are local tests, not device peak-memory measurements.

The inspected normal widget path already limits decoded resolution. Adding
`inSampleSize` to decoding this existing small preview would not establish a
real memory improvement. No production change is justified by this warning
alone, and disappearance of the Play recommendation is not verified.

This investigation does not measure device peak memory, Coil's runtime decode
size, multiple widget instances, or unusually large or damaged cache previews.
If a current-version warning or a reproducible memory problem occurs, measure
those paths before changing decoding behavior.
