# STAK v2.3 — focused changes from v2.2

This release starts from the complete verified v2.2 source (`ff73406139cd320e10cfb5e4301c6bb800b9bb72`, matching local tree `64e3d3e42fe3f6a7a07b12053ddaf8a52c7ccf72`). It does not include the broader v3 usability changes.

| Request | Change |
|---|---|
| Unified search | Every title search requests movies and series together, including legacy URLs with a media-type parameter. Search results have no Movies/Series selector. Independent paging and partial-failure handling retain successful results. |
| Smarter search | Existing exact-title, alternate-title, accent, word and typo relevance ranking is retained. TMDb supports a title/year fallback (e.g. Dune 2021) while preserving numeric exact titles (e.g. Blade Runner 2049). Existing whitespace/IME protection remains. Suggestions stay actionable when focus moves from the input. |
| Lower Discover type selector | Movies/Series remains a browsing control, centered below the search bar. It hides while searching, since search combines both types. Existing services, regional availability and filters remain functional. |
| Home search, no filter button | Home reuses the same unified search and four-column results. Its filter button is omitted. Clearing search restores the existing Home content and saved section order. |
| Remove explanation text | The visible Add to Library instruction paragraph is removed. An invisible accessible description remains for screen readers. Actual error messages and provider/security warnings remain. |
| Version | Android name 2.3.0, versionCode 26, same package `app.dmmstudio.cinema`. Code 26 exceeds the withdrawn v3 code 25; matching signing keys are still required for an update. |
| Remove v3.0 | The delivery workflow removes only the reviewed v3.0.0 release and tag after the v2.3 build gate passes and v2.3 is published. It checks the exact old target before deletion. The unmerged source branch remains a backup. |

## Preservation

The v2.2 Details action bar, source picker, tracking, Library, Activity, Settings and Home preferences are preserved. No new adaptive action bar, transfer records, watched Undo route, Library quick filters, Activity views or Home-default changes from v3 are included. TorBox, Jellyfin, TMDb, Cinemeta, Torrentio, Stremio addons, Torznab, embedded trailers and external playback are retained. No internal playback engine, VLC, new dependency, database reset or Linux port-8085 change.

## Verification

Local: 83 adapter regressions, 215 source/import checks and Android static configuration checks passed. Full install, ESLint, React tests, standalone production build, four mobile Chromium configurations, Capacitor sync, Gradle APK build, APK signature/assets checks and ZIP extraction are gated by GitHub Actions for the exact delivered commit. See `CI_TEST_RESULTS.md` in the source export and the release's linked workflow run for actual outcomes.

No live-provider, physical Android device, emulator or measured FPS testing is claimed. The original Android signing key is unavailable. The build compares its public certificate to published v2.2.0 and records the actual result in `validation/signing-compatibility.json`. A different certificate blocks an in-place update: **do not uninstall or clear data to bypass it; build with the original matching key**.

Dependency installation/full Android tooling runs on the repository's GitHub Actions runner because this workspace's shell proxy previously rejected npm access. Provider coverage, search spelling tolerance and streaming availability depend on real upstream data; no invented results or rankings are added.
