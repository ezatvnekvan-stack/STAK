# Architecture and development guide

STAK is a standalone Kotlin/Jetpack Compose Android application. Keep the existing package structure, navigation, encrypted `SettingsStore` and Room schema/migrations. The installed application ID is `app.stak.nativeapp.parity` for the preview distribution.

## Data and services

- Jellyfin is authoritative for playback progress and current watched state. Tracking is passive: do not introduce watched/unwatched UI or replay legacy pending playback writes. Historical observations stay account-scoped and are not erased merely because an item leaves the library. Do not infer exact watch duration from a watched flag.
- Find Sources combines explicitly configured Stremio addons and Torznab indexers. Results require TorBox cache verification. Preserve exact private `.torrent` metadata, progressive bounded checks, account-scoped memory caches, fresh confirmation checks and `add_only_if_cached=true`. Do not treat playback URLs as torrent metadata.
- TorBox management supports explicit user operations with account guards, no blind retries and named delete confirmations. Search, sorting, refresh and selection must not perform mutations.
- Zurg is an existing external service. Use WebDAV for browsing; reachability does not prove Linux mount health. Do not invent control endpoints or download media to the phone/server.
- The optional Docker library filter publishes only symlinks for explicitly approved complete torrents. Preserve encrypted approval outbox, credential scope/revision isolation, unique source matching and marker-based Jellyfin path verification. Android approvals alone do not filter an unmodified Jellyfin library.

The project intentionally does not expose an internal full media player, Sonarr/Radarr/Seerr integration or a mandatory STAK backend. Optional series Auto-add quality profiles and movie/show organization are now provided by the single self-hosted deployment/stak-server service.

## Optional accounts

`CloudAccounts` coordinates managed Supabase username/password authentication, logical snapshots and encrypted automatic backups. `CloudBackupCodec` encrypts compressed portable data on-device; device master keys and cloud sessions never enter a snapshot. `CloudSnapshotStore` restores Room/settings with an encrypted rollback journal before regular app models start. Successful restore recreates navigation and viewmodels. Preserve passive Jellyfin tracking: importing a snapshot must never replay media or watched mutations.

The optional service uses authenticated RPCs, per-user database access and revision checks. Restore precedes upload on sign-in; conflicts stop writes, and unknown acknowledgements are reconciled before retrying. Account switching must not upload another account's retained local dataset. Show only acknowledged save times. See `deployment/stak-cloud/README.md` for provisioning; deployment connection settings are supplied at build time, and an unconfigured build must not imply online protection. `CloudHostedIntegrationTest` runs only with explicit `stakHostedCloud=true` instrumentation opt-in, using synthetic data on the configured service.

## UI

Keep Doto, Space Mono and Hanken Grotesk typography, restrained red accents, rounded outlines, cinematic artwork light and glass navigation/dialogs. No dot-matrix background or pixel notches. Pause ambient motion during scrolling, modals, backgrounding and Reduced motion; avoid per-frame blur and self-referential capture.

Details chrome reserves layout space. Library uses a compact selector plus permission-gated real scan progress on Jellyfin only; TorBox uses counted status tabs, compact rows and long-press selection. Preserve underlying menus, collection/version navigation and partial-error retries when refining presentation.

`NothingMotion` builds on `StakMotion`: 160–300 ms touch/navigation transitions, one-time staged Details reveals, 420 ms dot-graph/count builds, cached-image crossfades and explicit state-change signals. Shared-element modifiers belong on artwork only, never on poster titles, ranks or the Details action layout. Stable lazy keys and saveable reveal state prevent repeated entrances on scrolling back. Read animation state in graphics layers or Canvas where possible; never animate blur radius or execute service calls from animation effects.

Reduced motion follows the live Android animator setting as well as the in-app setting. It disables spatial/count/graph animations and repeating skeleton/loader motion; basic panel transitions use short fades. Loaders and skeleton loops also stop outside the resumed lifecycle or while the global cinematic motion coordinator is busy. Accessibility exposes actual metric values throughout counting and selected calendar dates throughout movement.

## Verification

Use JVM tests for protocol, parsing, migrations and account/isolation rules. Use focused Android tests for navigation, read-only tracking, cache gating, confirmations, real scan-state reducers and layout geometry. Several older instrumentation tests are explicitly ignored because their manual watched controls were retired; do not restore those controls to satisfy obsolete tests.

Tests use synthetic credentials and controlled fixtures. Never run acquisition/deletion against a user's live account as an automated smoke test. Inspect rendered UI, test the optimized APK and verify encrypted-data preservation on in-place updates. Version names may restart at 1.0.0; Android version codes must continue increasing.

Private signing files, `.env`, SDK paths, generated binaries, screenshots and logs must remain untracked. Keep the official update certificate unchanged for published APKs. Public contributor builds deliberately use their local debug certificate when the official key is absent.

## Unified media server (1.2.0)

One Python process/container hosts the existing watcher API and organized library API under library/. Direct in-process handoff carries stable media identities. Old filter listener/token is optional compatibility inside the same container. Migration snapshots both ledgers, backs up Jellyfin config and preserves post-cutover records on rollback. Organized output replaces only owned symlinks. Films and shows are organized; automation remains series-only and cached-only. Android 1.1.1 imports server approvals, retains pending user edits and provides metadata-only identification. No watched-state writes or blind add replay are introduced.

Server1.2.2: approved movie/show folder layout is automatic even without provider ids. Technical/domain prefixes are removed from display folders, common series parts share season folders, optional year hints are consistent, and explicit file/parent season markers are supported. This is filesystem labeling only; acquisition still requires exact identities, consent and fresh cache confirmation. No provider ids are guessed. No new app build or configuration is required.


## Android 1.2.0: responsive loading and public lists

Interactive Details uses fastDetails: provider core metadata first, selected TMDb season on demand, optional OMDb ratings independently. Background TrackingCatalog keeps the full details path; partial interactive episodes must never become global completion evidence. Each season load is generation-guarded and retryable. Clicked artwork is retained while metadata loads. No watched writes or acquisition guards change.

Home publishes hero/rows independently; Trakt title rows publish ranked text before bounded batches of artwork. Search retains only matching prior results while debouncing. Public TMDb JSON uses a credential-hashed, bounded 15-minute metadata cache (192 entries, 16 MiB memory, 24 MiB disk); no Jellyfin, TorBox, source results or mutations enter it. Shared Coil artwork cache has a 160 MiB disk budget and 20% memory budget. Thumbnail dimensions are role-specific. Existing reduced-motion, lifecycle gates and artwork-only shared transitions remain in effect.

Discover → Lists reads public Trakt popular/trending/search and paginated mixed movie/show items. Only GET endpoints are allowed. No Trakt OAuth, tracking mutations or automatic additions. The existing Client ID setting is used. Saved public lists live in saved_public_lists preferences and enter the existing encrypted cloud snapshot. List text renders before artwork, visible-card preview work is bounded to three requests, and API errors retain a retry path. List page size30; search/list page size20. Unsupported person/season/episode rows are omitted; movie/show order is retained.

Find Sources uses a compact floating pill with a transparent surrounding footer. Layout space is still reserved so controls cannot sit under it. Original source/cache confirmation, private torrent handling, Auto-add consent and optional server compatibility remain unchanged.


## Android1.2.1 / code61: approved premium Home and top Details action

This supersedes the 1.2.0 floating-footer action. Find Sources is now a prominent light primary button inside the top Details management block, before Trailer/Watchlist. It has one saveable-identity entrance sweep, press feedback, a 58dp+ target and reduced-motion cancellation. There is no bottom source dock or reserved dock strip. Movie/series and individual-episode source search still use the same cached-only confirmation flow; no acquisition or watched-state logic changed.

Home uses a compact search-toggle/settings header with a status dot, no decorative provider labels, a larger fixed-height hero, full-width singleton Continue Watching, the existing two-up Coming Up, a wide #1 trending-movie spotlight plus ranked remaining posters, and a shared Movies/Series anticipated block only when both enabled anticipated sections are adjacent. Custom ordering and disabled rows are respected; preferences are not rewritten. Long-press menus remain available.

Hero pictures dissolve in one canvas: pager translation is canceled in graphics layers, with small transition-only scale/parallax; title/logo copy animates independently, page indicators draw their animated width in Canvas. Duplicate edge pages normalize to identical content for seamless cyclic paging. Only the visible page can receive artwork taps, and outgoing text actions are disabled. Shared transitions attach only to artwork. Autoplay waits8.5s and pauses when the hero leaves the viewport, vertical scrolling starts, a dialog is open, lifecycle is not RESUMED, or Reduced motion applies. There is no repeating per-frame blur or continuous zoom at rest. Reduced motion keeps manual paging and disables autoplay/spatial transforms.


## Android1.2.2 compact refinement

Supersedes the 1.2.1 size/emphasis choices: top Sources action is dark/outlined, 176–216dp wide and48dp touch height. Hero232–278dp, no View details CTA; root tap remains the action. Continue Watching uses low horizontal thumbnail cards, not full-width cover images. Preserve the source guards, menus and hero lifecycle/reduced-motion/cyclic animation behavior. See COMPACT-HOME-1.2.2.md for verification.
