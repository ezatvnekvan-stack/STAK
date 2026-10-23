# Exact Capacitor visual parity

The current target is the existing Capacitor interface. The separate premium redesign branch is not the implementation target. This branch continues the tested native implementation at `3921c27`, retaining search alias/numeric-title regressions, Discover filters, the normal-motion Find sources crash fix and Jellyfin password/API-key protocol fixes.

## Reference and scope

The unchanged frontend is the visual source of truth. `parity/reference-manifest.json` fingerprints its fonts, styles and assets. The 88 checked-in captures cover 22 routes at 360×880 and 412×880 in dark/light themes with fixture-only providers and reduced motion. Pixel comparison requires matching viewport, theme, font scale, data, scroll position and settled assets. Browser captures do not certify Android WebView, live-provider or physical-device behavior.

`STAK_REFERENCE_METRICS=1` adds computed DOM rectangles and typography/color/spacing metadata to the production reference capture. `STAK_REFERENCE_OUTPUT` allows reproducing captures outside the checked-in reference directory. Neither option changes the frontend.

## Native corrections in progress

- Keep full CSS-style line boxes instead of trimming first/last-line leading.
- Use the original light foreground and secondary-text colors.
- Include CSS borders inside connection-row heights.
- Match Home branding offsets, badge spacing, hero indicators and the navigation icon/label frames.
- Let empty Home shelves contribute no spacing; visible sections own their original margins.
- Let the Jellyfin connection paragraph wrap its action at narrow widths and remain inline when it fits.

## Acceptance and evidence

`parity/compare.py` reports exact differing-pixel counts, maximum channel differences and comparison images. Missing captures and mismatched viewports fail comparison coverage. `--require-identical` fails when any pixel differs. A low average difference is not a pixel-perfect result.

All native unit, lint and emulator gates remain enabled. Every correction requires the actual CI reports and screenshot review. The existing source-opening and Jellyfin login regressions remain enabled. Expanded overlay, keyboard, long-content, accessibility and normal-motion captures are still needed beyond the 22-route matrix. Complete functional parity and credentialed-provider/device behavior remain unverified.
