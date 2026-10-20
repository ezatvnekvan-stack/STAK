# Existing features completion — 2026-10-15

This branch preserves the complete previous Android source and the unpushed priority repairs. Scope is existing functionality only: no new route, provider, player, customization category or backend is introduced. The separate Linux application was not modified.

## Repaired/activated workflow inventory (24 groups)

1. Build lint configuration: one react-app ESLint configuration, explicit ES2021/globalThis, matching Hooks plugin line; hook/error checks retained.
2. Android minimum SDK: API 24 for Cordova Android 13; package identity unchanged.
3. Build/sync ordering: real production HTML/assets must exist before Capacitor sync; no placeholder index.html.
4. Movies/Series/All search: independent cursors, deduplication and partial failures.
5. Existing genre/year/rating/sort controls: affect actual returned data; query edits preserve filters.
6. TMDb Discover TV age filtering: uses the existing content_ratings capability; explicitly limited to the returned provider page. Movie/TV age options no longer mix.
7. Existing collection controls: show actual collection parts inside Discover; metadata cards keep working TMDb-only titles.
8. Title metadata: series network/status/date bindings and season stills/scores are populated from existing TMDb responses.
9. Ratings: dormant RatingBadge reused for existing title/hero/episode scores; visibility is consistent; Metacritic retains its 100-point scale.
10. EpisodeRatingsGrid: connected to existing series details and selected-episode/source/tracking state; no invented scores.
11. TonightCard: connected to the existing Surprise me Home preference, with actual runtime filtering and recoverable filters when no suggestion matches.
12. JellyfinHomeLibraries: connected to the existing Available to watch Home preference, with bounded preview requests.
13. Home sections: existing order/visibility controls govern all corresponding sections; shared query refresh avoids full remounts; the existing Upcoming/Because/Tonight loaders also receive the same refresh, and Upcoming cards preserve the selected episode.
14. Appearance/navigation/motion: light/preset readability, failed-save recovery, reduced-motion hero/haptic behavior and navigation deduplication.
15. Progress/history: refreshTracking retained and defined; per-episode dates survive local tracking and backups; calendar uses local dates and resolves saved TMDb IDs through the existing adapter.
16. Watchlist: actual list refresh and truthful guest/loading/cache states.
17. Torrent search: TMDb-to-IMDb resolution, visible source failures, exact episode selection and explicit pack mode.
18. Magnets/cache: retained original Torznab magnets and trackers; normalized supported hashes; independent custom sources do not require TorBox credentials.
19. TorBox management: active-status polling dependencies, visibility-aware polling, accurate partial bulk feedback and failed selections retained.
20. File actions: stale single/bulk/ZIP responses cannot populate another selected torrent; file ID zero does not unlock busy controls.
21. Provider HTTP: bounded guarded redirects fix catalog HTTP 307 handling without forwarding credentials across origins; public metadata deduplication.
22. Jellyfin authentication/settings: verify selected token/user before save, meaningful failures, reconnection without data reset, selected legacy credential migration and artwork invalidation.
23. Jellyfin browsing/queue/remote: no false empty result after errors; partial Next Up errors visible; remote commands gated by existing capability flag; episode links use parent-series identity.
24. Android lifecycle/overlays/storage: existing back handling selects the top dialog; foreground refresh is not duplicated; native credential memory changes only after persistence; source dialogs use existing accessible primitives.

## Dormant components

All four requested components are connected to existing controls/sections: RatingBadge, EpisodeRatingsGrid, TonightCard and JellyfinHomeLibraries. They reuse current data/providers. No additional Home widget ID or route was created.

## Verification actually performed

- 49 dependency-free adapter/persistence/transport tests passed, zero failures.
- 166 source modules passed structural/local import checks.
- Android configuration/XML/native registration checks passed, including API 24 requirement.
- git diff whitespace check passed.
- Full local dependency installation: failed with EPERM connecting to the environment proxy.
- ESLint, Jest and React production build: attempted; unavailable installed commands (exit 127).
- Capacitor sync: attempted; stopped by the intentional missing-production-build guard.
- Gradle debug build: attempted; distribution download fails on restricted socket access.
- No live-provider, emulator, physical-device, visual or performance test succeeded or was claimed.
- Existing/new React regression tests are included but were not run locally.

The full equipped GitHub Actions run subsequently succeeded: https://github.com/ezatvnekvan-stack/studio-dmm-android/actions/runs/38690707832. Actual results: 13 Jest suites / 57 tests, 49 adapter tests, 165 source modules, ESLint, standalone production build, Capacitor sync and Gradle debug APK build all passed. The confirmed artifact contains the source ZIP and compiled debug APK: https://github.com/ezatvnekvan-stack/studio-dmm-android/actions/runs/38690707832/artifacts/11951479831. Local EPERM/exit-127 records above remain historical local attempts, not the final CI result. Additional APK-content/signature checks are included in the final validation workflow and must be assessed from its actual result.

## Remaining limitations / intentionally skipped

- Live Jellyfin/TorBox/TMDb/OMDb/indexer/YouTube contracts and Android WebView behavior require real credentials and devices.
- A revoked/expired Jellyfin session must be reconnected; code cannot restore an invalid credential.
- A genuine torrent search with zero available releases remains empty; no release availability is invented.
- TV age filtering checks the current provider page; no unsupported TMDb TV discover parameter is invented.
- Cinemeta filtering/sorting is limited to supplied catalog pages, and some metadata can be missing.
- Jellyfin library matching/history still have existing bounded-result limitations; full remote-unwatched conflict reconciliation needs a separately engineered sync model.
- Watchlist series cache checks retain the existing S1E1 sample scope; labels must not be read as complete-series availability.
- YouTube embedding can be blocked; the existing external fallback remains.
- Hosted share links, server webhooks, automatic TorBox-to-Jellyfin filesystem transfer, background notifications, new playback engines, widgets, new profiles/settings and new search providers were intentionally not implemented.
- Full profile/key-rotation migration for TorBox-scoped watchlists is outside this completion pass; old namespaces remain untouched.
- CI debug signing is not guaranteed to match an already installed APK; a data-preserving upgrade requires the original signing certificate. Do not uninstall/clear storage to bypass a mismatch.
- Android minimum API is now 24 (Android 7), required by the existing Cordova dependency.

## Build/download

Use this branch's GitHub Code > Download ZIP or git clone followed by checkout of dmm-studio-existing-features-completion. Follow BUILD_ANDROID.md. Do not run cap add android over the included native project. Generated node_modules, build output, credentials and APKs are not committed. If CI compiles an APK, its workflow artifact contains the complete source ZIP with that debug APK inside.

## GitHub delivery

Verified Draft PR: https://github.com/ezatvnekvan-stack/studio-dmm-android/pull/2

Branch: `dmm-studio-existing-features-completion`. The main branch is unchanged. CI outcomes must be read from the actual workflow run; local offline successes do not imply a compiled APK.


## Source-to-repair traceability

Paths below are relative to `frontend/src` unless stated otherwise. Automated checks cover controlled data and requests; they do not certify provider availability or physical-device interaction.

| Groups | Original implementation / defect | Small correction | Verification |
|---|---|---|---|
| 1–3 | Root ESLint/CRACO settings conflicted; native minimum API did not satisfy Cordova; sync could run without compiled HTML | `../.eslintrc.json`, `../craco.config.js`, `../android/variables.gradle`, `../../scripts/verify_web_build.cjs` retain lint rules, require API 24 and real bundled assets | Actual CI lint, production build and Capacitor sync; Android configuration script |
| 4–6 | `pages/SearchPage.jsx`, `components/DiscoverFilters.jsx`, `lib/offlineAdapter.js`: mixed result cursors, ignored filters, wrong TV certification handling | Independent per-type pagination, real returned-data filtering and existing TV content ratings | Search stale-request/page-retry React tests and adapter fixtures; full genre/provider combinations still need live checks |
| 7–8 | `pages/DiscoverPage.jsx`, `components/MetadataCards.jsx`, `lib/offlineAdapter.js`: collection selection and metadata fields were disconnected | Existing collection-parts query, valid TMDb navigation, series/logo/season response bindings | Collection/TMDb adapter cases and production compilation; visual checks pending |
| 9–10 | `components/RatingBadge.jsx`, `RatingsBlock.jsx`, `EpisodeRatingsGrid.jsx`, `pages/TitlePage.jsx`: dormant components, inconsistent scale/visibility and episode selection | Reuse current scores and selected-episode state; visibility subscriptions | Actual React badge visibility/grid selection and episode-source/tracking tests |
| 11–13 | `components/HomeSections.jsx`, `TonightCard.jsx`, `JellyfinHomeLibraries.jsx`, `HomeRow.jsx`: stored sections/order were ignored or remounted; empty Tonight filter hid controls | Map all existing section IDs, shared refresh, real library previews and recoverable Tonight filters | Runtime-filter React regression and production build; drag/order/device layout still require visual smoke tests |
| 14 | `components/AppearanceSettings.jsx`, `NavigationSettings.jsx`, `MediaHero.jsx`, `lib/theme.js`, `appearance.js`, `haptics.js`, `i18n.js`: controls saved without applying or ignored failed storage | Apply existing tokens/settings, roll back failed saves, stop hero/haptics for reduced motion | Appearance React and storage/theme VM cases; native motion/system bar checks pending |
| 15–16 | `pages/ProgressPage.jsx`, `WatchlistPage.jsx`, `CalendarPage.jsx`, `lib/accountQueries.js`: undefined refreshTracking, UTC day shifts, misleading loading, guest queries unexpectedly enabled | Defined context refresh, local day boundaries/labels, explicit boolean enablement and actual list refresh | React retry/loading/guest account tests; history/backup adapter cases |
| 17–18 | `lib/torrentio.js`, `seasonPack.js`, `offlineAdapter.js`: TMDb IDs sent to IMDb-only addons, magnets lost, episode-range packs matched the wrong season | Existing external-ID resolution, original magnets and normalized hashes, season-bound episode ranges | Actual Torrentio fallback/season-pack Jest cases plus indexer/cache VM cases; real indexers pending |
| 19–20 | `pages/LibraryPage.jsx`, `components/FilesSheet.jsx`: stale async file responses and inaccurate partial/busy state | Guard by selected request/torrent and preserve failed selections | Production compilation and source review; live TorBox bulk/file race smoke tests remain required |
| 21 | `lib/transport.js`: native HTTP 307 treated as a fatal response | Bounded guarded redirects, no cross-origin credential forwarding, public request deduplication | Redirect/credential/loop/downgrade/dedup VM cases |
| 22–23 | `lib/jellyfinAuth.js`, `jellyfinConnection.js`, `offlineAdapter.js`, `components/JellyfinSettings.jsx`, `JellyfinLibrary.jsx`, `JellyfinHub.jsx`, `pages/JellyfinItemPage.jsx`, `RemotePage.jsx`: incomplete auth/session handling, silent queue errors and episode identities | Standard client auth, validate before save, independent connection migration, explicit partial errors and existing remote capability checks | Auth/resume/queue/library fixtures, actual library error/retry React tests; live server/remote client pending |
| 24 | `lib/nativeLifecycle.js`, `secureStorage.js`, `components/ui/dialog.jsx`: wrong overlay back target, duplicate invalidation, premature credential memory update | Use existing topmost-dialog primitives, one foreground refresh and update memory after vault success | Native registration/config checks and source review; Android lifecycle/Keystore upgrade testing pending |

Preservation check: all 232 tracked files in the preserved baseline remain present. No file was removed from that baseline. This is the verifiable source inventory; earlier conversational counts of “60 modified files” are not treated as a separate authoritative manifest.

Final regression correction: Home refresh now also reloads UpcomingStrip, BecauseRow and TonightCard without remounting the page or resetting their controls. Upcoming episode links preserve season/episode selection. A React regression verifies refreshed data and exact episode navigation; read the final CI result for its actual outcome. Small personalized helper rows retain their existing hidden-empty behavior when no usable metadata is available; no recommendations are fabricated.
