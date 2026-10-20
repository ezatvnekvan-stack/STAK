# DMM Studio Android 14.2 — Nothing redesign verification checklist

This branch builds on the complete previous Android development source (remote head 9249656). No existing tracked source files are deleted. All provider adapters and native plugin sources are preserved. This checklist describes implementation; actual build/test outcomes are recorded in the generated `CI_TEST_RESULTS.md` and the Actions run. It does not claim live provider or device verification.

## Home

| Request | Status | Implementation / qualification |
|---|---|---|
| Remove greeting, Add Magnet/Search header shortcuts, Go and Recent Searches | Completed | Compact real Jellyfin library summary replaces GreetingBar; search retains input/keyboard submission and existing filters. Historical recent-search storage is not erased. |
| Count actual Jellyfin movies and TV shows | Completed | Two user-scoped item-count requests; missing totals are errors, never fabricated zeroes. Disconnected/loading/error states remain distinct. |
| Improve closest/exact title relevance | Completed | Global ranking across movie/series and all loaded pages, normalized punctuation/accents, original titles, year disambiguation and bounded edit distance. Existing Cinemeta search supplemented by configured TMDb search. Results not supplied by providers cannot be ranked. |
| Smaller hero, only recent trending movies/TV | Completed | Real TMDb weekly trending, last two years, released titles, interleaved movie/series; compact artwork and no mislabeled popular fallback. Missing key/results hides the hero. |
| Compact IMDb mark/rating | Completed | Pixel wordmark and compact badges; actual OMDb IMDb rating only, correctly labeled TMDb fallback. Visibility preferences respected. |
| Continue Watching → normal Details | Completed | Reliable provider identifiers, parent-series resolution for episodes, preserved episode coordinates and safe manual search for unmatched items; no name-based auto-match. |
| Remove trending rank numbers and Ready on TorBox | Completed | Standard poster rails; deprecated Home section ignored without resetting saved layout or touching torrents. |

## Discovery

| Request | Status | Implementation / qualification |
|---|---|---|
| Premium browsing sections | Completed | Real Trending, Popular, TMDb Top Rated, New (90 days), Upcoming (one year), Hidden Gems and existing movie collections. Load more preserves actual provider pagination. |
| Streaming service browsing/filtering | Completed | Official TMDb region/provider catalogs, recognizable service chips when supported, complete provider dropdown, regional availability and attribution. No invented provider IDs or availability. |
| Netflix, HBO Max, Disney+, Prime, Apple and other supported services | Completed conditionally | Displayed when TMDb lists them for the selected type/region. Brand names reflect the provider catalog; no assumption of availability in every country. |
| IMDb Top 250 | Blocked by data source | No licensed reliable Top 250 feed exists in this project. TMDb Top Rated explicitly labeled; not presented as IMDb ranking. |
| Genres and decades | Completed | Inline genre chips/dropdown, decades and bounded year selections; intersect category date windows correctly for movies and TV. |
| Directors | Completed | Real TMDb people with Directing department, searchable names, existing person/filmography route; crew filmography added alongside cast credits. Popular sample is not a comprehensive director ranking. |
| Compact in-place filters | Completed | Expandable panel/dropdowns for genre, decade, years, rating, age, language, region/service, ordering and trend period. No filter-page navigation. |
| Accurate filtering and performance | Completed with limitation | Native discover parameters where supported. Trending keeps its real endpoint; unsupported filters/sorting apply to returned pages with an explicit note. TV US age ratings and trending availability need bounded extra requests (four at a time). Load more can uncover additional matches. |

## Details (highest priority)

| Request | Status | Implementation / qualification |
|---|---|---|
| Compact seamless backdrop/title logo | Completed | Single image/fade into theme background, no separate logo panel/seam/provider logos. Light theme uses readable text where artwork logos would lack contrast. |
| Smaller consistent rating marks | Completed | Pixel wordmarks for IMDb/TMDb/RT/Metacritic, correct source scales and existing per-source visibility. No fabricated scores. |
| Three-state Jellyfin availability | Completed | Green verified provider match; red only verified complete negative lookup; neutral disconnected, loading, partial, malformed or failed lookup. Selected episode availability remains separate from series availability. |
| Compact horizontal episodes/seasons without red outline | Completed | Smaller snapping cards, monochrome selection, watched markers, original episode coordinates and search/tracking actions. Specials season 0 retained. |
| One compact TV progress summary | Completed | Known watched/total episodes, completion%, remaining episodes, next episode, selected watched toggle and mark-season action. Separate selected-episode Jellyfin playback %, saved minutes and remaining time only when actual runtime/position exists. Unavailable metadata is explicitly identified. |
| Remove Episode Ratings, Tracking, Series Completion, More Like This, Trailers and Extras sections | Completed | Separate sections and trailer button removed from Details. Watched/tracking actions integrated above; underlying adapters/components remain for compatibility. No embedded player or new engine. |
| Image-only gallery/fullscreen | Completed | Actual TMDb title backdrops or existing title artwork fallback, horizontal image rail and accessible fullscreen dialog. No invented imagery. |
| Behind the Camera | Completed with metadata limitation | Movie directors/writers; TV creators and actual writing/explicit Showrunner credits, including aggregate credits. Executive producers are not guessed to be showrunners. After episodes for TV; after synopsis for movies. Missing credits stay absent. |
| Responsive Add to Library | Implemented; production browser gate enforced | Bounded single grid track fixes intrinsic width growth; compact season/resolution/sort/episode selection; advanced filters expandable; long source names wrap and action buttons retain their column. All source/cache/add workflows retained. |

## Global / preservation

| Request | Status | Implementation / qualification |
|---|---|---|
| Remove screenshot's unnecessary Jellyfin Play page | Completed | Exact source was `JellyfinItemPage`. Replaced with compatibility resolver for saved routes; no device/player panel. Dedicated existing Remote Control remains independently available. |
| Remove Interface Styles selector | Completed | Unified Nothing presentation. Legacy selection saved once under `dmm-interface-style-legacy`; safe migration tolerates denied storage and never resets other preferences. |
| Keep Dark/Light/System, AMOLED, accents and reduced motion | Completed | Existing settings retained, theme variables and focus styles honored, hero timing respects Android/browser and app reduced motion. No new preference categories. |
| Consistency, subtle gradients, compact controls and animations | Completed in source | Shared compact wordmarks, cards, monochrome episode selection, restrained transitions and theme-aware gradients. Device aesthetic/gesture validation still required. |
| Preserve providers, credentials, data and standalone operation | Completed in source | Existing TorBox/Torrentio/Stremio/Torznab/Cinemeta/TMDb/Jellyfin operations, encrypted vault, account scoping, backups and package identity retained. No data reset, Linux deployment changes, port-8085/preview dependency, VLC or new playback engine. |
| GitHub branch, commit, Draft PR to main | Completed | Confirmed Draft PR [#4](https://github.com/ezatvnekvan-stack/studio-dmm-android/pull/4), dedicated `dmm-studio-complete-nothing-redesign`; Actions and artifact links are recorded in the PR after verification. Never auto-merge. |
| Debug APK artifact | Build dependent | Actions builds and inspects APK and exports source+APK together. Actual outcomes generated per run; never infer success from presence of source files. |

## Validation and risks

- Local dependency-free adapter tests exercise real adapter contracts using explicit test fixtures, not live success responses in the app. Structural/import and Android config checks run locally.
- CI installs dependencies, enforces ESLint, runs Jest and adapter regressions, compiles standalone React, performs production Chromium smoke tests, synchronizes Capacitor, compiles Gradle debug APK, checks archive/assets/native plugins and verifies APK signing.
- Chromium fixtures exist only in `scripts/browser-smoke.cjs`. They check 360/412px dark/light, source sheet bounds with long titles, horizontal episodes, Discovery controls, Home cleanup, migration and uncaught exceptions. This is not Android WebView/device testing or live provider authentication.
- Provider credentials, real servers, region correctness, artwork/cache performance, keyboard/gestures/system bars, encrypted upgrades and resume behavior still require live Android testing. TV progress totals reflect supplied metadata; specials are available but excluded from main season completion. Jellyfin episode-state lookup retains a 1,000-item bound and reports incomplete negative lookups as unknown.
- Direct provider access requires user-configured keys as before. API quotas and metadata licensing apply. Regional catalogs are informational, not subscription entitlement checks. HTTP LAN credentials/media are unencrypted; use trusted LAN or HTTPS. Arbitrary LAN cleartext is enabled natively with existing URL checks; native image fetches avoid unsafe mixed-content WebView settings.
- Install upgrades require the original signing certificate. Ephemeral CI debug keys may differ from an installed build. Do not uninstall or clear storage to bypass a mismatch; use the original key to preserve app data. Secure credentials are intentionally excluded from exports/backups.

See `BUILD_ANDROID.md` for rebuilding, `CI_TEST_RESULTS.md` for the artifact's actual run, and `TEST_RESULTS.md` for test scope.
