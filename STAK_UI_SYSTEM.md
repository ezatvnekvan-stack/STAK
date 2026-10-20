# STAK 2.2 — unified Nothing UI and Discover

Android `2.2.0`, versionCode `24`, unchanged package `app.dmmstudio.cinema`. Based on the complete v2.1 branch. Main and existing releases remain untouched. No credential reset, database conversion, backup-format change, new provider backend or playback engine.

## Shared design system

`frontend/src/styles/stak.css` is the control/token source. It replaces the old competing control definitions in `index.css` and `App.css`; the existing `n-*` classes are compatibility aliases to the same contract. Existing handlers, imports and settings continue working. Theme/accent/AMOLED values still come from the existing theme/appearance modules, including light mode.

| Reusable component / contract | Standardized behavior |
|---|---|
| `ui/button.jsx`, `StakUI.Button` | Primary/default, secondary, outlined/outline, subtle/ghost, destructive, link and icon-only variants; compact/regular/large padding; 44px minimum target, common type/border/radius/icons, focus/disabled/loading feedback. Historical generic `n-btn-red` becomes monochrome primary; actual destructive controls keep an explicit danger treatment. |
| `StakUI.Chip` / `SegmentedControl` | Consistent selected states, real values and accessible pressed/group labels; same geometry as other actions. Existing `n-chip` controls use the same tokens. |
| `StakUI.Surface`, `ui/card`, `n-card` | Shared charcoal/light surface, border, radius and content spacing. Media thumbnails and Details artwork retain their intentional image layouts and soft context surfaces. No whole-card press animation on noninteractive settings/statistics. |
| `ui/input`, `ui/textarea`, `n-input` | Shared field height, type, padding, border/focus, placeholder and disabled states. File-selection buttons receive the same action geometry without changing file handling. |
| `NothingSelect`, `ui/select` | In-WebView accessible menus; common trigger, selection, item height, radius, surface and popup layer. No native Android radio-list selector. Existing values/onChange adapters preserved. |
| `ui/switch`, `ui/checkbox` | Shared monochrome checked states and visible focus; switch has a 44px hit region around its compact track; checkbox has an expanded hit area. Existing checked/onChange and settings storage retained. |
| `ui/tabs`, `FilterDisclosure` | Common pill selection/disclosure controls with compact expanded content; active state and keyboard behavior retained. |
| `ui/dialog`, `alert-dialog`, `drawer`, `sheet` | Shared overlay, surface, heading/description, radius, spacing, layer tokens, scroll bounds and 44px close control. Drawers do not scale the background page; native-back-compatible close actions remain available. Side sheets retain their side positioning. |
| `ui/popover`, dropdown/context/menubar/hover/tooltip wrappers | Common menu surface and layering; compact content retained. Poster long-press actions use the same buttons and keep their watchlist/library operations. |
| `ui/badge`, `glyph` | Shared noninteractive badge typography, border and padding; provider/rating artwork and truthful status colors remain distinct. |
| `ProgressIndicator`, `ui/Progress`, legacy `DotProgress` | One accessible clamped indicator with segmented, dot and thin continuous variants. A single transform-driven fill, not dozens of animated nodes. Indeterminate scans expose no fabricated percentage. |
| `Bits.PageTitle`, empty/error/loading controls | Shared page hierarchy and action styles, including recovery screens; loading/empty/error information and retry actions retained. |
| Motion | Shared 140/200/240ms fast/normal/page tokens and common easing. OS and saved reduced-motion preferences disable indicator/spinner/page/press effects. Existing hero gestures retain their reduced-motion handling. |

Small decorative hero pagination and the activity heatmap retain their purpose-built layouts; they are not restyled as large action pills. Full-image poster/episode/gallery controls retain image aspect ratios. These are intentional compositions, not separate button themes.

## Screens and preserved workflows

| Screen | Updated / preserved |
|---|---|
| Home | Shared cards, scan action, real scan progress/results, resume indicators and page controls; floating Home-only branding retained. |
| Discover | Unified structure described below; provider and filter changes affect actual requests. |
| Movie Details | Shared trailer/watch/source actions, actual playback indicator, smooth full-viewport artwork; metadata, gallery, cast/crew, rating preferences retained. |
| TV Details / episodes | Separate series completion and selected/resume playback; episode/season mark and undo logic retained; compact episode sheet with full metadata/source actions. |
| Watchlist | Shared tabs/toggles/sort/refresh/empty states; three posters per phone row; local data and undo preserved. |
| Jellyfin Library | Shared controls/dropdowns/cards/scan indicator; All/server libraries, verified version grouping, artwork, cached recovery, matching and connection errors preserved. |
| TorBox | Shared torrent progress, filtering/sorting, file drawer, magnet/file input dialogs, bulk actions, deletion confirmation and export controls. Existing management/link/duplicate handlers remain. |
| Activity | Shared filters/statistics/continue rows/progress; merged history, twelve-week day filtering, paging and Jellyfin synchronization retained. |
| Calendar | Shared controls/cards/page hierarchy; actual upcoming episode logic and existing filters retained. |
| Search | Shared search controls and standalone filter drawer. Discover uses the same bar but opens its single filter disclosure; provider-constrained results use supported TMDb search/filter APIs. Cinemeta and IMDb-ID search remain available. |
| Settings | Account, Sources, Jellyfin, Appearance and Advanced groups inherit the same control primitives. Credential validation/secure storage, indexers, theme/rating/motion/haptics, navigation/Home ordering, backups and toolkit actions retained. |
| Jellyfin Remote / compatibility links | Shared controls and device selection, connection/retry/match dialogs; playback stays in the external client. |
| People / sharing / backups | Shared headings, cards, menus, dialogs, fields and actions. Metadata links, local hash lists and backup merging remain. |

## Unified Discover

Order: heading and Movies/Series selector → smart search → compact expandable Filters and removable active chips → always-visible provider selection → curated channels → paginated results → Directors. Text-search results appear below the same filters/providers rather than creating a second filter mode.

There is no Browse/Services switch. Netflix, HBO Max, Disney Plus, Apple TV, Hulu, Prime Video and supported Paramount artwork use real regional provider IDs/logos. Any additional provider returned by TMDb can be selected through More streaming services. Choose All services, tap the selected logo again or remove its active chip to clear it. Missing/unavailable providers never silently show an unfiltered catalog.

Curated channels are Popular, Trending now, Top rated (explicitly TMDb), New releases, Upcoming and Hidden gems. Only the active channel fetches a paginated result set, avoiding six eager duplicated rails. Genre, decade/year, real IMDb minimum, language, US age certification, sort, watched and known-library filters remain. Active selections can be removed individually or reset together. Existing region and filter preferences are preserved; old Browse/Services view values are simply no longer used to hide providers.

Filters and loaded query pages survive opening Details and returning. Cached preferences initialize immediately; a user change updates that cache before its save finishes and cancels any older preference read. Preference writes are serialized so an older rapid selection cannot overwrite a newer one. Client-only watched/library filters reuse loaded results rather than re-requesting providers.

IMDb filtering verifies each loaded page through existing supported metadata, excluding unknown scores. Trending/provider/TV-certification constraints can require per-title lookups; concurrency and caches remain bounded. The UI reports page scope and partial rating failures. IMDb Top 250 and a fabricated critics ranking are intentionally absent: no reliable licensed source is configured. Hidden gems are explicitly defined using TMDb votes/popularity; they are not labeled critics' scores. TV Upcoming concerns first releases, while the existing Calendar covers individual episodes.

## Episode sheet and progress correctness

The episode sheet uses an 80×54 thumbnail, compact title/date/rating, a three-line expandable synopsis, scrollable body and persistent source-action footer. It is at most 70% of the viewport / 500px and fits narrow screens. Escape, close and the existing Android back handler dismiss it. Full synopsis, real IMDb rating when supplied, metadata retry, exact season/episode source selection, watched/undo, actual selected-episode progress and confirmed external Jellyfin links remain accessible. No new internal player, remote playback engine, or server importer is introduced.

Overall TV progress counts unique valid season/episode coordinates, excludes Specials as previously requested, and displays watched/total, percentage and remaining episodes. Partial playback does not count as completed. The individual playback bar remains separate. Mark/unmark and season actions reuse the existing local/Jellyfin mutation and synchronization logic. The compact sheet never displays a different episode's resume position. Unknown episode availability remains unknown rather than enabling a series fallback as if the episode were confirmed.

## Verification and limits

Local checks: 82 adapter regressions, 215 source/import checks, Android XML/configuration, browser-script syntax and diff checks. The matching GitHub Actions run performs actual dependency installation, ESLint, all React tests, strict production build, four production Chromium width/theme configurations, Capacitor synchronization, Gradle debug assembly, APK contents/signature and ZIP extraction/byte comparisons. `CI_TEST_RESULTS.md` in the exported source and the final Draft PR contain the actual outcomes. Existing regression tests are preserved; new tests cover the shared controls, filtering/provider contracts, genuine IMDb-ID resolution, cached/queued preferences, completion arithmetic, watched/undo and compact episode actions.

Browser coverage uses isolated test-only external responses; it is not live-service validation. It covers Home, merged Discover/search, movie/TV Details and episode/source dialogs, Watchlist, Library, Activity/legacy Progress, Calendar, all Settings groups, TorBox file/magnet sheets, Remote, hash lists and People. It checks dark/light layouts, narrow widths, reduced motion, page overflow, provider clearing/pagination and navigation restoration. Individual provider operations and every hardware/browser combination cannot be exhaustively proved by these checks.

No physical-device, emulator, credentialed-provider or measured 90/120-FPS success is claimed. Device scrolling/perceived contrast, real playback/embed policy, LAN/TLS/authentication, large libraries and native dialogs still require real Android validation. Adding a cached torrent to TorBox does not import it into Jellyfin without an external server workflow. Metadata, episode totals and availability are limited by the configured providers/server data.

The workflow additionally verifies and compares the public signer certificates of the current APK and the published v2.1.0 APK. The exported `validation/signing-compatibility.json` records MATCH, DIFFERENT_SIGNER or UNVERIFIED; a mismatch is reported, never bypassed or silently treated as compatible.

The APK is a debug build. Package identity, Keystore implementation and data formats are preserved, but safe in-place installation also requires the original matching signing certificate, which this CI environment does not possess. Preserve backups; rebuild with the original signing key. Do not uninstall or clear data to bypass signature errors. No signing material or credentials are committed or packaged.
