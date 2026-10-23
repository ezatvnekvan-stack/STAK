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
- Match the original settings panels, Doto section headings, field spacing and selection chips, including the Sources dashed form and Jellyfin authentication modes.
- Restore Taskbar before Home Screen and the inline genre-shelf controls, keeping persisted order and visibility behavior.
- Restore the Data & app toolkit, local-backup and About layout. Native encrypted-backup safeguards remain enabled; export/import dialogs and refreshed diagnostics still require further parity work.

The settings pass at `549df5c` passed 55 unit tests, lint, 32 first-width emulator tests and five second-width tests in [Actions run 39962968841](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39962968841). The reports contain zero failures, errors or skips. All 88 captures have matching viewport dimensions and still contain differing pixels.

Measured 360-pixel dark-theme average channel differences improved from the first layout pass to the settings pass: Home & browsing 11.593 → 3.970, Jellyfin 12.131 → 3.925, Sources 12.948 → 4.293. These figures help locate differences; they are not acceptance thresholds or a pixel-perfect result.

The next pass restores the original artwork-only title header, title font sizing and wrapping action controls, the two-column account statistics, compact metadata checkboxes, select typography, CSS dot-gradient pixels and the wrapping remote-control buttons. Gallery images now open a dismissible viewer. Fresh emulator verification is required after these changes.

## Acceptance and evidence

`parity/compare.py` reports exact differing-pixel counts, maximum channel differences and comparison images. Missing captures and mismatched viewports fail comparison coverage. `--require-identical` fails when any pixel differs. A low average difference is not a pixel-perfect result.

All native unit, lint and emulator gates remain enabled. Every correction requires the actual CI reports and screenshot review. The existing source-opening and Jellyfin login regressions remain enabled. Expanded overlay, keyboard, long-content, accessibility and normal-motion captures are still needed beyond the 22-route matrix. Complete functional parity and credentialed-provider/device behavior remain unverified.

## Remaining gaps

- All routes still need exact glyph, color, border, spacing and raster comparisons; browser screenshots cannot establish Android WebView equality.
- Source/filter/episode dialogs, dropdown menus, keyboard, image failure/loading, animation frames, long content and large font/accessibility states are not covered by the complete original/native matrix.
- Metadata credential controls still use the existing native Save & verify flow. A proposed additional stored-key Test connection action was rejected by automatic approval review because it would send stored TMDb/OMDb keys to their official APIs; that new request path was not added. Existing verification logic is unchanged.
- Native encrypted-backup dialogs and refreshed toolkit diagnostics still differ from the original, including the absence of the server's MongoDB diagnostics. The app does not invent successful server statuses.
- TorBox file/share/storage workflows, Jellyfin hierarchy and advanced remote behavior, Discover suggestions, credentialed providers and physical devices still require functional validation and further parity work.
