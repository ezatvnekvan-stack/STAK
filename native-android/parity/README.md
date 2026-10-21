# Original STAK references and native validation

The current user direction allows UI and motion improvements with a beautiful Nothing-inspired style while retaining the original app's features. The React/Capacitor app remains the functional and visual reference. Exact screenshot equality is no longer a release gate. The native port remains unfinished; no pixel-perfect or full-feature claim is supported.

## Reference evidence

`reference/` contains 88 original production React screenshots: 22 route/state captures, at 360×880 and 412×880 CSS pixels, in dark/light themes, with reduced motion and intercepted **test-only** provider data. `reference-manifest.json` identifies the source commit and hashes of the original styles/tokens. These browser references do not establish Capacitor-specific behavior or live account correctness. The original frontend is unchanged.

Regenerate only from the original production frontend build, with Playwright/Chromium available:

```sh
STAK_UPDATE_REFERENCE=1 STAK_CHROMIUM_PATH=/usr/bin/chromium node scripts/capture-original-reference.cjs
```

`OriginalReferenceTest` injects test-only HTTP fixtures into the native repository and image loader; these fixtures are in the Android test APK only. Emulator captures exclude system bars, record the measured viewport, and retain both themes. A viewport mismatch invalidates comparison. The CI runner sets display density and font scale explicitly.

```sh
python3 native-android/parity/compare.py --native native-evidence --output native-comparisons
```

This produces original/native/difference images and a coverage report. A similarity metric does not certify pixel-perfect parity. Each screen and interactive state needs visual review and behavior checks.

## Reviewed checkpoints

At `d87b001`, [CI run 39505164911](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39505164911) passed build, unit tests, lint and emulator checks. All 80 expected captures had matching dimensions. All 80 still required visual corrections; no screen was certified. Review found inherited Material letter spacing, missing Space Mono Bold, header/hero measurements, the theme-preview layout, and tracking/calendar differences.

The implementation ports the original rating-logo cells, original Rive asset/state machine, measured appearance controls, calendar geometry, runtime rules and episode journal transactions. Hosted emulator comparison results are recorded below. Custom colour dialog geometry and all animation timing still need comparison. Rive is native; the unchanged asset SHA-256 is `9b9c68b6319e5a186f99bb94ba5b3001f8a272cc3a4fd6a688e67b8bdf7310b4`.

Home now supports inline unified search, IMDb identity, original result ranking, clearing and pagination, with populated and empty-query references added at both viewports/themes. Suggestions, aliases and all search failure/filter states still require parity work.

The normal-motion browser captures in `reference/motion/` contain the original 94×52 theme illustration after settling, using an explicit integer pixel clip without resizing. The original CSS placement may be fractional. Reproduce them with `STAK_REFERENCE_MODE=motion` and the same capture command. They do not verify continuous frame timing. The Android motion test retains dark/light/static crops and requires the bundled illustration to render beyond the static icon.

At `f38175a`, [CI run 39509185904](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39509185904) passed build/unit/lint and four Android tests, including the 40 captures at 412×880. The motion test failed when TextureView bitmap readback returned null with a hosted GPU warning. The 360-pixel pass did not execute. These results are not a successful emulator validation. The next test uses system screenshots and preserves the second viewport pass even if another functional test fails, while retaining the failing exit status.

At `39d333a`, [CI run 39510808710](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39510808710) produced all 88 captures with matching dimensions; all 88 still have differences. The functional run failed the cleared-search semantics assertion and the Rive preview after changing theme. A retained dark motion crop showed the static icon rather than the illustration. The next implementation waits for the native artboard/surface, uses the Android Canvas renderer and a real Android settling timer, and preserves functional-run reports separately from the second screenshot pass. This correction still requires device verification.

## Remaining feature and validation gaps

The following original behaviors remain incomplete or unverified. Screenshot differences now inform design review rather than blocking every intentional UI improvement:

- All Discover filters, provider-service tiles, directors, search suggestions and persistent browsing preferences.
- Exact theme preview/Rive behavior; artwork palette sampling; ambient drift; every original transition and sheet geometry.
- All Home shelves, personalization, genre rows, section/taskbar reordering and persisted navigation.
- Cached source availability, source filters, matching, technical details and non-hash Stremio streams. Native Torznab/Stremio setup, encrypted account-scoped storage and concurrent hash-source aggregation are implemented; new emulator coverage is pending.
- Watched dates, movie/episode undo, season confirmations, Jellyfin episode sync, full history/progress rules and calendar filters/movie releases.
- Jellyfin collection hierarchy, item pages, storage/server tools, exact remote controls and account isolation.
- TorBox selection, bulk actions, file action parity, saved magnet lists, share creation/view/import and storage/status/filter controls.
- Original backup settings/Home/share coverage, encrypted export/import and migration across accounts.
- Loading, empty, failure, populated, long-content, keyboard, sheet/dialog and Android Back states for every route.
- Live provider/device behavior, supported Android versions, accessibility font scales and animation timing recordings.

Keep release notes explicit about unfinished features. A complete native replacement still requires feature coverage, interaction checks and device validation; screenshot equality is not required by the latest user direction.

At `5025b8e`, [CI run 39512211032](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39512211032) passed build, 16 unit tests, lint and six first-pass Android checks, plus the second viewport capture. All 88 native captures had matching viewport dimensions; all 88 contained pixel differences. Retained dark/light motion crops were visually inspected: both render the original grayscale Rive illustration, and reduced motion renders the static icon. Continuous animation timing has not been visually verified. This checkpoint predates the latest account controls.

The current design pass adds tactile navigation with animated ink, a spring-driven selection and press response, shorter outgoing fades and restrained incoming movement. Shared switches animate track/thumb colours and position, and fields animate their focus border. Reduced-motion settings snap these transitions. Native source settings retain the original add/test/enable/delete controls with a more spacious Nothing-inspired panel. Local build, 22 unit tests, lint and test-APK compilation pass for these changes; hosted checks and screenshot review are still pending.
