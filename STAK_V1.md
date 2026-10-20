# STAK v1.0.0

Android versionCode **21**, versionName **1.0.0**. The monotonic code preserves Android upgrade ordering despite the new public v1 naming. Package identity remains `app.dmmstudio.cinema`.

## Scope and request checklist

| Area | Status | Delivered behavior / limit |
|---|---|---|
| Nothing identity and existing features | Complete | Existing monochrome surfaces, custom menus, hero motion, reduced motion, trailer dialog, compact gallery/crew, horizontal borderless episodes and external playback retained. No VLC or internal movie/episode player. |
| Remember Discovery choices | Complete | Media type, section, category, period, region, genre/year/rating/language/service/sort and watched/library filters survive navigation/restart; account-specific encrypted Android preferences. Legacy region becomes the initial fallback. |
| Hide watched / known library titles | Complete, bounded | Actual saved tracking and provider IDs filter loaded results. Unknown titles remain visible. The library index checks up to 500 accessible movies/series in one shared request and labels incomplete coverage. No title-name guessing or false negative availability. |
| Availability on media cards | Complete, bounded | Compact Jellyfin indicator for a verified ID match. TorBox cached indicator only after an actual successful source cache check, retained for five minutes in memory, up to 100 titles. Unknown is never presented as unavailable or cached. |
| Search relevance and suggestions | Complete for returned metadata | Exact/localized/original/alternate titles, year and existing typo scoring; up to four real query results offered beneath the shared search. No extra provider request per suggestion. Can rank only titles returned by configured services; does not invent unavailable aliases. |
| Collections | Complete for loaded metadata | Release order/provider order, watched count/percentage, hide-watched and real paginated catalogs. Partial/keyword catalogs explicitly say **loaded titles**; universe tagging is not an official exhaustive chronology. Series completion requires a reliable completed-title record; partial episodes do not count as completed shows. |
| Jellyfin diagnostics | Complete | On-demand server reachability, session and library-access checks independently report failure. Shows HTTPS or explicitly unencrypted local HTTP. Does not expose credentials or raw error bodies. |
| Reliable title resolution/manual correction | Complete | Unmatched library/Continue Watching titles offer a local manual-match dialog searching real Cinemeta metadata. Episode mappings target the parent series and retain selected season/episode. Reverse availability verifies the mapped item against the current user. No server metadata changes; up to 1,000 mappings per server/user. |
| External-playback return | Complete at refresh layer | Native foreground refreshes both account and separate Jellyfin library query families, and runs a throttled progress merge into local tracking (at most once per 30 seconds). Newer manual watched choices retain priority; tracking writes and sync are serialized. Current availability, Continue Watching/Next Up, watched-state and resume data are fetched from Jellyfin; actual external-client reporting still determines server progress. No invented playback telemetry. |
| Cached Jellyfin browsing | Complete for loaded pages | Up to 24 encrypted presentation snapshots per server/user. Views and exact collection/search/sort/watch-filter/pages can display saved data after errors, with last-updated time, safe error and retry. No cross-user/server fallback, media paths or tokens in snapshots. Artwork is still bounded in-memory; offline image persistence is not claimed. |
| Scan additions | Complete, bounded | Existing inline real task/progress monitor now compares recent movie, series **and episode** IDs. Shows episode coordinates and honest net title counts. Latest 30 observations are not an exhaustive file/scan log; no fake progress. |
| Add to Library preferences | Complete for existing controls | Remember resolution, sort, maximum size, cached-only, quality flags and existing HUN language preference. Temporary filename text remains per page. No unverified new language metadata. Full source/pack/episode/cache/indexer/copy/filter capabilities retained. |
| Source context/ranking | Complete | Clear title/year/season/episode/pack context; compact expandable release names; ranking explanation (cache/resolution/seeds/HUN/size penalty), source tags and quality inspector retained. |
| Duplicate torrent recovery | Complete for loaded account torrents | Case-insensitive existing hashes open their real TorBox file sheet rather than sending a duplicate add. TorBox presence/ready status is separate from Jellyfin availability. Other devices concurrently adding torrents can still race the provider; refresh/retry remains necessary. |
| Download/import state | Complete and honest | Existing torrent status/file controls remain; ready badges derive from provider state. Adding to TorBox does not pretend to import into Jellyfin. Automatic copying/import requires a downloader/mount/server workflow outside this standalone app. |
| Details next action | Complete | New compact action uses the existing source picker for the actual next unwatched episode; existing select-next, mark episode/season and tracking controls remain. Long synopsis expands; technical info remains expandable. |
| Quick actions and Undo | Complete for watchlist | Existing long-press menu and Details watchlist action offer Undo, guarded by explicit expected/desired state. A stale Undo cannot blindly toggle a newer change. Existing watched/unwatched controls remain reversible; a full playback-position/season-history transaction is intentionally not fabricated. |
| Navigation/offline | Complete at UI/cache layer | Existing back/forward scroll restoration retained and bounded to 100 entries. Discovery filters persist across restart; Jellyfin collection/search/sort/watch-filter choices survive Details navigation during the session and clear on server change. Offline notice, saved-library freshness, targeted retry and network-recovery refresh added. Offline mutations still need their actual provider; no silent queued fake successes. |
| Smoothness | Retained + bounded work | Existing high-refresh hint, reduced-motion support, adjacent hero preloading, shared metadata queries and bounded authenticated artwork cache retained. Suggestions reuse results; catalog is one shared bounded background request; local snapshots serialize writes. Scroll memory, cached-title signals and library snapshots are bounded. No measured 90/120 FPS or instant-network-loading claim. |
| Android/GitHub delivery | CI-gated | Dedicated `stak-v1` branch; Draft PR to main; v1.0.0 release only after lint, tests, production browser/build, Capacitor, Gradle, APK signature/assets and source ZIP checks pass. Existing tags/releases are protected against overwrite by a different commit. |

## Architecture / affected files

- `frontend/src/lib/experience.js`: preference validation, safe cached presentation fields, provider-ID matching, loaded-result filtering and collection release ordering.
- `useExperience.js`: shared account-scoped read, non-destructive late hydration and explicit failed-save feedback.
- `offlineAdapter.js`: existing standalone direct API adapter extended with local experience preferences, encrypted bounded snapshots, local mappings and actual Jellyfin diagnostics/catalog/scan observations. No dependency on port 8085 or hosted preview.
- `AppContext.jsx`: shared bounded catalog/cached-title signal, serialized explicit watchlist Undo; account retirement/data-isolation preserved.
- `DiscoverPage`, `SearchPage`, `CollectionExplorer`, `PosterCard`: remembered browsing, real suggestions, watched/availability filtering and compact matched indicators.
- `JellyfinLibrary`, `JellyfinDiagnostics`, `JellyfinDetailsLink`, `JellyfinMatchDialog`, `JellyfinScan`: on-demand diagnosis, cached browsing, user-confirmed metadata resolution and observed episode additions.
- `TitlePage`, `TitleProgress`, `StreamRow`, `LibraryPage`: remembered existing source controls, next-episode search, duplicate recovery and compact contextual source presentation.
- `nativeLifecycle`, `AppShell`: foreground/reconnect invalidation, offline status, bounded scroll memory.

## Data, privacy and compatibility

Existing watchlists/tracking/credentials/home settings/backups are not reset or renamed. New preferences are separate from legacy `/prefs` Home settings; `dmm-backup` version 1 remains compatible. New device-specific browsing preferences, cached library snapshots and manual match mappings are deliberately excluded from old-format backups. Android Keystore storage is reused; browser preview storage has weaker OS protection. Only legitimate supported provider APIs are used. Public APIs use HTTPS. User-configured LAN HTTP sends authentication/media information without transport encryption; use trusted LANs or HTTPS. The existing Android LAN network configuration is preserved.

The release contains a **debug APK**, not a production signing key. An installed APK can update in place only when signed by the same certificate. This CI environment does not have the original device-installed APK's signing key. Never clear app data or uninstall merely to bypass a signature mismatch; preserve an app backup and use the matching signing key for a safe upgrade.

## Validation and measurable targets

Local completed: 75 dependency-free adapter regressions, 199 JavaScript/JSX source/import structural checks and Android manifest/XML/native configuration verification. These are not a substitute for the React compiler or Gradle. Dependencies and Android SDK are not available in the local shell; actual install/lint/React/browser/Capacitor/Gradle results are produced by the matching GitHub Actions run and included in `CI_TEST_RESULTS.md` in the downloadable source ZIP.

New automated coverage includes preference isolation/late hydration; duplicate hashes; clipboard rejection; guarded watchlist Undo; next-episode source selection; cache outage/isolation/privacy; mapping direction/parent episode/type safety; incomplete availability; diagnostics partial failure; scan episode contract; alternate title relevance and collection ordering. All pre-existing tests remain.

Production Chromium checks exercise 360/412px dark/light layouts, responsive source sheets, long releases, Nothing menus, episode rails, trailer errors, catalog pagination, shared search/four-column results and gesture navigation using explicit intercepted test-only provider fixtures. This is not live Jellyfin/TorBox/TMDb/YouTube validation or an Android WebView/device test.

Device acceptance targets (not claimed measurements):

- Nothing Phone 3a: no blank screen during provider errors, corrupt settings, server changes or foreground return; preserve watchlist/tracking/credentials across upgrade.
- Warm cached interactions: visible press feedback within 100 ms; no unnecessary loading overlay while valid cached data exists.
- On a capable 120-Hz device, target p95 active-animation frame work below 8.3 ms; at 90 Hz below 11.1 ms. Profile an installed release-signed build with Android frame timeline/Perfetto, not browser test fixtures. Honor system refresh mode and reduced motion.
- Scroll 1,000 titles and navigate 50 times: bounded cache counts, no sustained heap growth after GC, no artwork-induced layout jumps.
- Offline, slow LAN, 401, 403, 429, timeout and resume after external playback: truthful saved/unknown/error states, functional retry, zero automatic mutation retries/duplicate adds.
- LAN HTTP/HTTPS with Jellyfin 10.x: authenticate regular/admin users, scan task status, long scans, new episodes, metadata-missing shows, manual match and user/server switch. Test permission-limited responses.
- Direct API timing depends on provider/network; no app can make uncached remote artwork or requests literally instant. Measure p50/p95 cold/warm startup, route response and artwork timing on the real device before promising budgets.

## Remaining limitations / intentionally not fabricated

No live provider credentials, emulator, physical Nothing phone, measured battery/frame results or original production signing key are available here. Manual match search can find only real provider-indexed titles. Library index covers at most 500 titles; unknown matches stay visible. Snapshots cover loaded pages and not offline video/artwork. Scan recent 30 IDs can miss large batches. No automatic downloader-to-Jellyfin transfer infrastructure, new metadata/rating provider, IMDb Top 250 scrape or in-app movie player is introduced. Embedded YouTube trailers still depend on provider permission, WebView version and origin policy; the existing external fallback remains.
