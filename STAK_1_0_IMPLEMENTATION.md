# STAK 1.0.0 implementation

Date: 2026-10-19. Development branch: `stak-discover-202`. Original baseline: `v2.0.2-polish`, `d4a2991892d3272e9f9868632b938eae1d9e7e43`. This work continues that ancestry and the previous Discover/Tracking/collections/Details/watched-state deliveries. No 3.x/4.x application code is imported.

## Concrete changes

| Area | Implementation and source evidence |
|---|---|
| Shared UI | `frontend/src/styles/refinement.css`, imported by `src/index.js`: consistent page/section hierarchy, poster title/meta/rating ordering, two-line titles, quieter surfaces, readable navigation labels, modal surface texture, empty states, safe-area spacing, compact/reduced-motion inheritance. Existing theme/accent/ambient renderer remains authoritative. |
| Home header/hero | `AppShell.jsx`, `MediaHero.jsx`, refinement CSS: compact actionable TorBox status, shorter header and hero, at most six featured choices, tighter typography, artwork readability. Library scanning remains in Jellyfin Library server actions. |
| Home shelves | `HomeSections.jsx`, `HomeRow.jsx`, `DeferredSection.jsx`: three complete phone posters; configured order/hide preferences preserved; approaching shelves mount within a 240px observer margin; search unmounts the feed and its effects. One-time mounting retains normal query caching. |
| Viewing queue | `JellyfinHub.jsx`: separate Continue Watching and Next Up groups, real episode names, remaining time and progress for resume items; same-series deduplication retained. |
| Release eligibility | `metadata/catalogRoutes.js`, `lib/home.js`: TMDb top-rated titles require a release date not in the future and at least 200 votes; released-year shelves exclude upcoming titles. The existing Upcoming shelf remains independent. |
| Settings | `AccountPage.jsx`, `SettingsGroup.jsx`: compact rows with visible summaries, focused URL-addressable pages, browser/Android navigation through the existing router. Unknown old section URLs return to the index. TorBox, metadata, indexers, Jellyfin, appearance, Home/navigation and data/app controls remain reachable. |
| Metadata credentials | `MetadataSources.jsx`: one active credential interface per provider; independent save/test/remove retained; visibility toggle and API-key help retained; Save & verify checks a replacement before persistence. Legacy duplicate OMDb/TMDb form components remain in source but are not mounted by Settings. |
| Connection status | `ConnectionStatus.jsx`: TorBox is verified by its actual account read; saved Jellyfin configuration is labeled Configured until an explicit bounded diagnostic succeeds; indexers are labeled configured, not network-verified. Rows navigate to the corresponding settings. |
| Appearance | `AppearanceSettings.jsx`: live token-driven preview, retaining dark/light/system, AMOLED, custom/preset accent, rating visibility, compact layout, haptics and atmosphere/reduced-motion controls. |
| Artwork calendar | `CalendarMonth.jsx`, `CalendarReleases.jsx`, `CalendarPage.jsx`: seven-column artwork month grid, +N additional-release markers, Today and selected-date treatment, Month/Week/Agenda views, release-type filters and a cinematic selected-day agenda with real episode links/watched badges. Titles remain readable below the month grid rather than squeezed into tiny cells. Failed day artwork restores theme text/contrast; failed agenda backdrops restore the poster/title heading instead of hiding the title. |
| Calendar data | `calendarModel.js`, `metadata/calendar.js`, `trackingModel.js`: seven-day civil-date ranges, device locale weekday labels, saved view/type preferences, backdrop/still/poster reuse from metadata already fetched. Last-loaded time is shown without claiming provider freshness. Date labels reuse a bounded formatter cache with invalid-locale/date fallbacks; production browser coverage caught and then cleared a month-rendering RangeError. No new artwork-metadata requests; existing bounded season selection and partial-failure handling retained. |
| Version/build | `frontend/package.json` and `android/app/build.gradle`: 1.0.0 / code 37, same application ID and signing configuration. `appVersion.js` also supplies the About screen and Jellyfin client headers while retaining the existing Jellyfin client/device identifiers. `README.md` replaces misleading old product/version text. CI validates the PR head and only publishes after its test/build gates pass. It reuses the hosted runner's preinstalled SDK instead of an unbounded command-line-tools replacement, with strict checks/timeouts for missing required components. |

## Preserved previous work

Discover's unified search/provider browsing/filter sheet and scroll restoration; Watchlist/Jellyfin/TorBox search, grid/list/filter and selection workflows; Details' seamless backdrop, source header, match warnings and compact action dock; local-first/manual tracking; app-wide Jellyfin watched reconciliation including explicit unwatch preservation; three-across mobile media layouts; backups/account isolation; native upload, playback/opening, remote controls and shares. Poster watched/watchlist/progress indicators now have explicit accessible semantics; artwork does not repeat the visible title in screen-reader output. Home shelf reorder/toggle/add controls have accessible labels. Existing regression suites cover these areas and remain in the validation workflow.

## Regression and visual verification

New/extended tests: `CalendarPage.test.jsx`, `calendarModel.test.js`, `SettingsGroup.test.jsx`, `DeferredSection.test.jsx`, `ConnectionStatus.test.jsx`, `HomeSections.test.jsx`, `MetadataSources.test.jsx`, `PosterCard.test.jsx`, and `scripts/tests/adapters.test.mjs`.

Production browser scenarios in `scripts/home-settings-visual.cjs` and `scripts/tracking-visual.cjs` cover Home card geometry/deferred shelves, focused Settings navigation/credential deduplication, appearance persistence, reduced motion/AMOLED/compact controls, artwork actually rendered in the calendar, seven-day Week navigation/type filtering and existing Month/Agenda keyboard/persistence behavior. Existing Discover, collections, Details, source picker, watched-state, Remote, Shares and Person checks are preserved. Widths: 360px and 412px; themes: dark and light.

Local dependency-free checks passed: source/import structures, Android configuration, adapter suite and shell/script syntax. The reviewed build at `e50d195126279061ffb570ad40d4f5b4e3e76534` passed 55 React suites / 233 tests, all 109 adapter tests, all four browser configurations and APK compilation/identity/signature checks in run `39246821118`. The final publication commit is validated again. Full dependency/build/browser results must be read from the exact release commit's GitHub Actions run and artifacts; this document does not assert a build before it finishes. Release assets include identity, public certificate, signing comparison, SHA-256 checksums and an exact-commit manifest.

## Performance evidence and limits

The prior source mounted every enabled Home section even under a hidden search container. The current source unmounts Home during search and gates secondary shelves using IntersectionObserver. Regression tests prove that offscreen child effects have not mounted and mount once upon intersection; production browser checks verify deferred shelves and three-card geometry. Hero candidates are bounded to six instead of ten. Calendar images reuse summary/season data and do not add image-metadata requests. These are verified request/mount behavior changes, not measured startup/FPS/memory improvements. No virtualization or heavyweight dependency was introduced.

## Release and installation limits

Visible version is 1.0.0, but Android versionCode increases to 37. Package ID is `app.dmmstudio.cinema`. Existing data/backup formats and secure-storage identifiers are unchanged. The existing `v1.0.0` release belongs to older code and is preserved; this delivery uses the distinct `stak-v1.0.0` tag with release title STAK 1.0.0.

The build remains a **debug-signed release variant**. Temporary CI debug certificates differ from existing installations; versionCode alone does not allow updating a differently signed app. A matching original private signing key is unavailable here. No uninstall, app-data deletion, signing-key replacement, force-push, merge or historical release deletion is performed or recommended.

## Remaining verification

- Real Jellyfin/TorBox/indexer credentials and private artwork, revoked sessions, provider errors/quotas and live release accuracy.
- Physical Android installation/update using a matching signing certificate; WebView/back/keyboard/system-bar behavior; TalkBack and increased system text size.
- Device performance baselines, large private libraries and memory measurements.
- Browser screenshots use intercepted test fixtures, not personal libraries or artwork. System-theme and AMOLED toggles retain existing behavior; they are not proof of every physical display mode.
- Calendar remains limited by the existing provider's coverage; it does not invent exact release times or promise complete worldwide schedules.
- No Sonarr/Radarr/Seerr/Trakt, embedded movie player or automatic TorBox-to-Jellyfin ingestion was added.
