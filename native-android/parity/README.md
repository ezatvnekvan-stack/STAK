# Original STAK references and native validation

The current user direction allows UI and motion improvements with a beautiful Nothing-inspired style while retaining the original app's features. The React/Capacitor app remains the functional and visual reference. Exact screenshot equality is no longer a release gate. The native port remains unfinished; no pixel-perfect or full-feature claim is supported.

## 0.3 Beta checkpoint (2026-10-21)

`0.3.0-native-beta.1` adds account-scoped local data, encrypted portable backups, persisted Home customization, paginated Jellyfin watch-state sync and cached-TorBox source filters. Locally on an API 35 x86_64 emulator configured to the CI viewport, and in hosted [run 39575016639](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39575016639), `assembleDebug`, 38 unit tests, `lintDebug` and all 21 instrumented tests pass. The two previously failing emulator checks now pass: the Home search-result click uses a unique test tag, and the rejected TorBox key test waits for the account store's settled error state before asserting the badge. See [`DEVELOPMENT_STATUS.md`](../DEVELOPMENT_STATUS.md).

These results use injected HTTP fixtures. They do not certify live TorBox/Jellyfin/TMDb/indexer behavior, physical-device performance or screenshot parity. The historical checkpoints below remain records of their own commits.

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
- Home behavior with a TMDb key, full personalization rules, and the no-TMDb fallback. Without TMDb, multiple discovery shelves can repeat the Cinemeta popular list and filter-based shelves can show a key-required error.
- Non-hash Stremio streams, complete technical-detail coverage and every cached-source edge state. Account-scoped indexers, encrypted storage, concurrent hash-source aggregation and cached-TorBox/quality/title filters are implemented.
- Watched dates, movie/episode undo, season confirmations, full history/progress rules and calendar filters/movie releases. Paginated Jellyfin watch-state sync is implemented and fixture-tested.
- Jellyfin collection hierarchy, item pages, storage/server tools and exact remote controls. Account isolation and paginated watch-state sync are implemented.
- TorBox selection, bulk actions, file action parity, saved magnet lists, share creation/view/import and storage/status/filter controls.
- Remaining backup settings/Home/share coverage and migration edge cases. Encrypted export/import, optional connection credentials and password validation are implemented.
- Loading, empty, failure, populated, long-content, keyboard, sheet/dialog and Android Back states for every route.
- Live provider/device behavior, supported Android versions, accessibility font scales and animation timing recordings.
- Public signing continuity: 0.3 Beta is debug signed for local evaluation; the permanent signing setup is still owner work.

Keep release notes explicit about unfinished features. A complete native replacement still requires feature coverage, interaction checks and device validation; screenshot equality is not required by the latest user direction.

At `5025b8e`, [CI run 39512211032](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39512211032) passed build, 16 unit tests, lint and six first-pass Android checks, plus the second viewport capture. All 88 native captures had matching viewport dimensions; all 88 contained pixel differences. Retained dark/light motion crops were visually inspected: both render the original grayscale Rive illustration, and reduced motion renders the static icon. Continuous animation timing has not been visually verified. This checkpoint predates the latest account controls.

The current design pass adds tactile navigation with animated ink, a spring-driven selection and press response, shorter outgoing fades and restrained incoming movement. Shared switches animate track/thumb colours and position, and fields animate their focus border. Reduced-motion settings snap these transitions. Native source settings retain the original add/test/enable/delete controls with a more spacious Nothing-inspired panel. Local build, 22 unit tests, lint and test-APK compilation pass for these changes; hosted checks and screenshot review are still pending.

The next motion pass connects the selected grid poster to a poster on the title hero, with a 360 ms bounds transition and reverse navigation. Posters have spring press feedback, real artwork fades in over 180 ms, hero indicators animate width/ink, icon controls compress on touch and segmented labels animate with selection. A dot grip replaces the default sheet handle. Longer account email addresses now have a full-width selectable card. These are intentional design improvements under the latest user direction, not pixel-parity corrections. The instrumented motion showcase retains a test-fixture video with a manually advanced frame clock; it is not a frame-rate benchmark or a physical-device recording.

At `7b254c7`, [CI run 39548685815](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39548685815) passed build, all 22 unit tests, lint and all eight first-pass Android checks, plus the second viewport capture. This includes TorBox key replacement/sign-out and indexer setup, toggling, account scoping, aggregation, partial failures and credential separation. All 88 native captures match the reference viewport dimensions; their visual differences are expected review material, not a pixel-parity claim. This checkpoint predates the poster/account-layout/motion-recording pass.

At `014a8f5`, [CI run 39549667119](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39549667119) passed build, unit tests and lint, and eight Android tests; the ninth failed only its recording-retention assertion after all showcase interactions passed. The retained MP4 is valid (8.5 seconds; 412×928). Android’s shell-command API did not interpret `&& echo` in the assertion; the next test reads file size with `stat`. All 88 native screenshots have matching reference viewport sizes and contain differences. Sampled movie/series, account, source and motion frames were visually reviewed. They support layout and transition progress, not pixel equality or device frame-rate claims. Home’s TorBox badge now verifies the saved key and exposes pending/error states; its new fixture test is awaiting the next emulator run.
