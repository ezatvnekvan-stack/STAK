# Original STAK parity gate — not satisfied

The original React/Capacitor app is the specification. No redesign or animation improvements are authorized until visual **and** functional parity are verified. The published 0.1.0 native alpha is not a replica. This branch is work in progress and must not be published as one.

## Reference evidence

`reference/` contains 80 original production React screenshots: 20 route/state captures, at 360×880 and 412×880 CSS pixels, in dark/light themes, with reduced motion and intercepted **test-only** provider data. `reference-manifest.json` identifies the source commit and hashes of the original styles/tokens. These browser references do not establish Capacitor-specific behavior or live account correctness. The original frontend is unchanged.

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

The next implementation ports the original rating-logo cells, original Rive asset/state machine, measured appearance controls, calendar geometry, runtime rules and episode journal transactions. These changes require a new emulator comparison. Custom colour dialog geometry and all animation timing still need comparison. Rive is native; the unchanged asset SHA-256 is `9b9c68b6319e5a186f99bb94ba5b3001f8a272cc3a4fd6a688e67b8bdf7310b4`.

## Blocking gaps

No screen has yet passed the complete parity gate. In addition to screenshot corrections, the following original behaviors remain incomplete or unverified:

- All Discover filters, provider-service tiles, directors, search suggestions and persistent browsing preferences.
- Exact theme preview/Rive behavior; artwork palette sampling; ambient drift; every original transition and sheet geometry.
- All Home shelves, personalization, genre rows, section/taskbar reordering and persisted navigation.
- Full source aggregation, Torznab/indexer management, cached availability, source filters, matching and technical details.
- Watched dates, movie/episode undo, season confirmations, Jellyfin episode sync, full history/progress rules and calendar filters/movie releases.
- Jellyfin collection hierarchy, item pages, storage/server tools, exact remote controls and account isolation.
- TorBox selection, bulk actions, file action parity, saved magnet lists, share creation/view/import and storage/status/filter controls.
- Original backup settings/Home/share coverage, encrypted export/import and migration across accounts.
- Loading, empty, failure, populated, long-content, keyboard, sheet/dialog and Android Back states for every route.
- Live provider/device behavior, supported Android versions, accessibility font scales and animation timing recordings.

Main UI components have been rewritten toward the original; that is implementation progress, not a parity claim. Release remains blocked until these gaps and the per-screen differences are resolved and verified.
