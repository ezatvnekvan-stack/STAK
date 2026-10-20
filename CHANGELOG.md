# STAK 2.0.2 — Compact source picker and visual consistency
- Reuse the Library Jellyfin mark across scan actions, Details and poster badges.
- Compact Details action bar with preserved 44px touch targets.
- Add source-picker poster/title/release information; remove redundant heading and cache count.
- Slightly strengthen adaptive artwork colour and remove search-match underlines.
- Preserve source filters, refresh, cache checks, tracking and existing data.

# STAK 2.0.1 — Ambient cinematic refinement
- Shared subtle Dot Matrix and graphite glow on all screens and overlays.
- Persistent intensity, optional motion, adaptive-colour and appearance-reset controls.
- Poster-free cinematic Details, cached backdrop palette and compact official Jellyfin action.
- Reduced-motion/background pausing; existing integrations, settings and data retained.
- CI-verified debug-signed APK; original signing key required for compatible updates.

# STAK 1.0.0
- Strip source maps/web-only Android assets; enable release R8/resource shrinking.
- Temporarily sign release APKs with the debug configuration; retain package identity.
- Align STAK branding and versionName 1.0.0 / versionCode 27.
- Remove Hungarian UI/docs and keep English metadata.
- Add optional passphrase-encrypted credential backups; keep default exports credential-free.
- Use TMDb-only search with a missing-key Cinemeta fallback; publish tagged CI APKs.

# STAK v2.4.1 — English-only metadata

- Metadata always requests English (`en-US`); removed the Metadata sources language chooser and redundant translation fallback requests.
- Existing Hungarian/other metadata-language preferences safely normalize to English, preserving selected sources, rating options, credentials, watchlists and tracking.
- English title logos take precedence over language-neutral artwork; foreign-language logos are not a fallback.
- Preserved original-language content filters and regional provider availability; they do not change metadata language. TVDB remains excluded.
- Added adapter, React and production browser regressions for legacy preferences, enforced English requests, removed language controls and English/neutral artwork selection.
- Android versionName 2.4.1 / versionCode 28. A new CI-gated release preserves v2.4.0. Actual build/test/signing outcomes accompany CI exports; no live-provider or device testing claim.

See METADATA_SOURCES.md for behavior and limitations.

---

# STAK v2.4.0 — Metadata source control

- Added account-scoped Metadata sources controls for TMDb/Cinemeta search, movies and series, secure TMDb/OMDb key status/testing, and Hungarian/English labels.
- Extracted metadata providers, identity resolution, field normalization, persistent caching, catalog routing, search and calendar logic; reduced offlineAdapter.js instead of growing it.
- Replaced merged search results with one selected source, canonical IMDb resolution/deduplication, TMDb language support and Bearer read tokens.
- Preserved OMDb ratings/episode ratings/settings and IMDb filtering, including Cinemeta browsing; added persistent quota cooldown, optional list ratings and labeled TMDb list scores (20+ votes).
- Disabled stream lookup when IMDb is unavailable. Existing watchlists, tracking, Jellyfin/TorBox configurations and backup compatibility remain.
- Calendar uses lightweight current/next-season TMDb episode dates instead of full series metadata per title.
- TVDB integration was excluded at the owner's request. There is no TVDB provider module, authentication, key/PIN setting, season-order setting or host entry. Old source preferences normalize to TMDb without deleting saved data. TVDB identifiers can still be mapped through TMDb external IDs for interoperability; no TVDB service is contacted.
- Expanded adapter, React and production browser regression coverage. Actual CI results accompany release exports; no live-service/device test claim.

See METADATA_SOURCES.md for source behavior, security details, build instructions and limitations.

---

# STAK 2.3.0

Focused update from v2.2: unified movie/series search, smarter title/year matching, Home search without filters, Discover browsing selector below search, and removal of visible Add to Library instruction text. The broader v3 changes are not included. See STAK_V2_3.md.

# STAK 2.2.0

Centralized Nothing UI tokens/control variants across existing screens, shared dialogs/menus/toggles/fields/cards/motion, unified Discover browsing and regional providers including Prime Video and real provider-filtered IMDb-ID search, persistent filters and queued saves, compact episode actions/synopsis, accurate unique-episode completion with separate playback, and shared segmented/dot progress across scan/torrents/Home/Activity. User data, integrations and external playback preserved. Full component/screen checklist: STAK_UI_SYSTEM.md.

# STAK 2.1.0

Screenshot-driven polish: floating Home-only branding/status and Jellyfin scan mark; full-viewport ambient Details artwork and smooth hero fades; bottom technical facts removed; consistent rounded trailer, watched and Discovery controls; aligned episode thumbnails/title heights; compact full-tile service artwork. Existing data, provider integrations and actions preserved. Full checklist: STAK_V2_1.md. Android versionCode 23.

# STAK 2.0.0

Home inline scan and icon branding; simplified Browse/Services Discovery with genuine IMDb filtering; three-column Watchlist; merged Activity with separate taskbar Calendar/Watchlist; version-deduplicated All Library; compact playback/watched Details and full episode popup; cached-only sources/default packs/no Specials; ambient artwork and search whitespace/stacking fixes. Full request checklist and limits: [STAK_V2.md](STAK_V2.md).

# STAK v1.0.0

Remembered Discovery/source choices, real availability/filtering and collection progress, search suggestions/aliases, Jellyfin diagnostics/local manual matching/encrypted cached pages, foreground/reconnect refresh, episode scan observations, guarded watchlist Undo, next-episode source search and duplicate TorBox recovery. Existing Nothing UI, integrations, source controls and user data retained. Android versionCode21; CI-gated v1 release. Full checklist and limits: [STAK_V1.md](STAK_V1.md).

# STAK 14.6.0 — Nothing UI refinement

Inline Library scanning/results, concise storage header, Library heading, removed Home Because you watched, clearer Discover Browse/Services/Collections, anchored long-press title actions, hidden Details Specials, borderless episode cards and reorganized Add to Library retaining every source control. STAK display brand, icon and dot-matrix launch reveal preserve package/storage/backup identities. Verified APK publishing to GitHub prereleases is gated on all checks. See STAK_UI_UPDATE.md.

# Android 14.5.0 — Discover search, four-column results, collection catalogs and Jellyfin scan

Reuses Home search on Discover; removes the top Home library summary; adds four-column search/skeletons; Jellyfin logo, true movie/show counts, deduplicated/partial media storage totals and server-reported animated scan monitoring with observed additions. Collections & universes searches actual TMDb franchise collections and keyword catalogs, including MCU alias expansion. Full checklist and limitations: DISCOVER_LIBRARY_SCAN.md.

# Android 14.4.0 — smooth presentation, Jellyfin Library and viewing activity

Shared Home/Details metadata caches, adjacent hero preloading, asynchronous artwork, no artificial splash delay, supported high-refresh Android hint and lighter poster effects. Compact episodes remove TMDb episode scores. Jellyfin Library now browses one cached paginated collection at a time with real server search/sort/watch filters. History, Progress and Calendar use compact Nothing surfaces, timelines, bounded lists and safer cached query lifecycles. See SMOOTH_LIBRARY_ACTIVITY.md for the full checklist and runtime limits.

# 14.3.0 — Nothing menus, full service browsing and in-app trailers

Replaces native grey selector dialogs throughout the app; adds visible source resolution chips; removes source-season watched totals; restricts Discovery to six requested services with regional paginated catalog grids; gives the hero real genres/certifications and restrained animated swipes; restores a top-of-Details embedded YouTube trailer dialog with graceful blocked-video recovery; compacts gallery and crew. See NOTHING_MENUS_TRAILERS.md for the per-request checklist and verification limits.

# 14.2.0 — Complete Nothing Android redesign

Home cleanup, real Jellyfin library counts, global title relevance, compact recent-trending hero, provider/region Discovery, compact Details progress/availability/crew/gallery, responsive Add to Library and safe appearance migration. The screenshot Jellyfin playback page becomes a normal-Details resolver. All integrations and user storage retained. Full per-request checklist, affected areas, limitations and verification scope: [NOTHING_REDESIGN.md](NOTHING_REDESIGN.md).

# Nothing details polish — latest pass

See NOTHING_DETAILS_POLISH.md for this pass: external-only trailers, horizontal episode rails, Jellyfin selection/device/refresh reliability and restrained accessible motion. Actual new CI results are recorded in the latest workflow and generated CI_TEST_RESULTS.md; earlier successful counts below refer to their named commits. No live-provider or device success is claimed.

---

# Verified completion delivery — 2026-10-15

- Preserved all 232 baseline source files; repaired/activated 24 existing workflow groups and all four requested dormant components. No new product screen, provider, player, setting category or backend dependency.
- Fixed guest query enablement, per-type search cursors/filters, season-bound episode packs, current metadata/collection bindings, rating visibility, episode tracking/actions, Home ordering/refresh, recoverable Tonight filters, settings failed-save behavior, TorBox stale/partial operations and Jellyfin connection/queue/library/remote failure paths.
- Resolved actual CI lint/test bootstrap failures and ambiguous Tailwind timing utilities; retained ESLint/React Hooks checks.
- Installed only supported Android SDK packages in CI; compiled the standalone React assets, synchronized Capacitor and successfully assembled a real debug APK.
- Actual successful CI results: 13 Jest suites / 57 tests, 49 adapter tests, 165 structural modules, native configuration, web build, Capacitor sync and Gradle APK build. See TEST_RESULTS.md for exact evidence and untested runtime limitations.
- Added final APK-content/signature verification to the existing build workflow; its actual outcome is reported by CI rather than assumed.
- Documented data-preserving signing requirements and existing integration/backend limitations. No credentials, dependencies, caches or generated APKs are committed.

---

# Existing-feature completion — 2026-10-15

See `EXISTING_FEATURES_COMPLETION.md` for the 24 repaired/activated workflow groups, 4 connected dormant components, precise scope, verification and remaining limitations. Latest offline checks: 49 adapter tests, 163 source modules, Android config checks. Full local builds remain blocked; no APK/device success is claimed.

---

# Priority screenshot and stability repairs — 2026-10-14

See `EVOLUTION_STATUS.md` for the complete implemented/deferred scope. This pass repairs guarded catalog redirects, Jellyfin session verification/reconnection and misleading empty states, canonical torrent-search IDs, episode controls, source errors, search filters, watchlist refresh, Progress synchronization, Home ordering/query reuse, credential transactions, artwork invalidation, light themes, dialog/back behavior and bulk-operation feedback. The Nothing visual identity and standalone Android configuration are retained.

42 dependency-free adapter regressions passed. Full build/device verification is pending; no APK success is claimed. The earlier source-repair changelog follows unchanged.

---

# Changelog — V13 Android repair

Date: 2026-10-14. This is a source repair and partially verified build candidate, not a declaration of complete production readiness. No original DMM backend/database/container was changed.

## Critical failure paths

- Fixed both HomeSettings and DiscoverFilters assuming genres were strings. TMDb sends genre objects; rendering those objects or calling toLowerCase on them crashed screens. Genre names now feed Cinemeta/home filters; TMDb Discover retains numeric genre IDs.
- Restored persisted dark/light/system themes, accents and interface styles. Removed hidden theme/accent controls and forced-dark/red implementation. Connected reduced animations, compact layout, AMOLED black and individual rating visibility to stored appearance state.
- Normalized malformed persisted flags and list state. Added provider envelope/list validation and safe account-state guards so objects cannot be treated as torrent/watch/tracking arrays. API failures surface as errors instead of successful empty data.
- Retained the global recovery screen and added screen boundaries inside the shell and individual settings groups, with navigation back to Settings. Credential validation runs before saving, and native persistence is awaited. Connection fields cannot change while their validation request is pending.
- The original historical TorBox black-screen response was not available for live reproduction. Audit established several unsafe response/render paths that can produce a fatal render after login; these paths were repaired and tested with malformed-response fixtures. No live recurrence test is claimed.

## Android and standalone operation

- Included Android Gradle project, manifest, resources and build launchers; none existed in the source archive. Added two native plugins for Keystore storage and bounded multipart torrent-file upload.
- Added LAN cleartext network security config, public-provider HTTPS exceptions, URL validation, system-only certificate trust and backup/transfer protection. Removed unnecessary mixed content and global fetch patching; direct Capacitor HTTP remains used explicitly.
- Select the standalone adapter automatically on native platforms, even when a build environment flag is omitted. Removed native service-worker registration/stale asset reuse, the nonfunctional hosted APK link, server-address setup prompts, and remote analytics/dev overlays.
- Added Android back handling for fullscreen/dialogs/keyboard/navigation/root minimization, foreground account invalidation and system bar updates. These native behaviors require real-device testing.

## Provider/API work

- TorBox: enforce successful response envelopes; normalize lists/files/progress; validate magnets/hash cache input; use multipart create-torrent requests; add .torrent picker and fixed-provider native upload; retain supported management operations and real download links; expose partial operation failures; never retry a mutation blindly.
- Shared transport: explicit connection/read timeouts, cancellation/discard of obsolete native results, at most one bounded GET retry for transient server/rate-limit responses, respect long Retry-After by surfacing a retryable error, redact provider-error bodies, and bounded 60-second public metadata caching.
- Jellyfin: add user login, preserve API-key/user selection, password masking and state reset, authenticated lazy artwork with deduplication/four-request concurrency/75-entry cache, library pagination, item navigation, restored detail/remote routes, movie/series details, episode selection, real resume positions and the correct Unpause command. History resolves episode SeriesIds to series IMDb IDs rather than treating episode IMDb IDs as show identifiers. Sync now stores tracking records and actual last-sync/statistics locally.
- TMDb: fix canonical/numeric ID handling, TV date/sort parameters, popular/hidden categories, real movie collections rather than mislabeled popular movies, title fallback, certification lookup, logo field mismatch, recommendations and actor routes. TMDb averages remain TMDb ratings. OMDb settings now use the direct adapter rather than bypassing it toward a missing backend.
- Indexers: isolate failures, validate URLs/capabilities, parse XML and base32 hashes, deduplicate normalized hashes, include seeders/peers and magnet attributes, use native Torrentio requests on Android, and explicitly warn about Torznab results without hashes. No unsupported TorBox endpoint or production fake success response was introduced.
- Trailers: preserve embedded YouTube playback, add load/error/timeout/retry states and external fallback. Cross-origin provider rejection cannot be reliably diagnosed from iframe load alone.

## Remaining screens and persistence

- Restored actor, calendar, Jellyfin item, remote and unknown-route handling. Library cards now open details; Calendar is reachable from Lists.
- Connected previously excluded recently-added/available/surprise rows, personalized genre rows to local watchlist genres, and labeled the former 'New seasons' row as recently added series rather than pretending it detects new seasons.
- Fixed watchlist added_at/created_at sort compatibility. Local watchlists, indexers, manual tracking and Jellyfin can work without a TorBox account in the standalone build.
- Serialized local read/modify/write mutations to prevent lost updates. Added local manual watch history when no Jellyfin server is configured.
- Added real calendar/upcoming aggregation with bounded concurrency and partial failure reporting. Catalog sorting is deterministic within loaded pages; TMDb Discover performs provider-side sorting.
- Added local backup/export/import with credential-free whitelisting and merging missing records. Hosted share links remain explicitly unavailable without infrastructure; native share exports magnets.
- Added reviewed duplicate deletion preserving a preferred copy. Fixed toolkit error-state mix-up, dialog touch targets/scroll containment, and stale file-download links crossing torrent selections. Failed magnet/file additions preserve retryable input.
- Removed duplicate metadata rendering and repeated requests from the ignored completionOnly path, and made home row failures visible/retryable.

## Dependencies and verification

- Simplified CRACO to required aliases/Jest mapping/lint configuration; removed unrelated remote development overlays. Replaced incompatible ESLint 9 with the CRA-compatible ESLint 8 line. Added required Capacitor lifecycle/browser/share/status-bar dependencies.
- Removed obsolete Yarn-only resolution/package-manager declarations: they were not applied by npm and could force incompatible development-server versions. No dependency vulnerability audit or successful installation is claimed.
- Added 33 dependency-free adapter/persistence/theme/appearance tests, two React appearance regressions in one Jest file, a source-structure/import/export checker, Android config checker and reproducible build instructions.
- Actual verification and unexecuted checks are recorded separately in TEST_RESULTS.md and FEATURE_VERIFICATION.md. See validation/source-changes.json for the source-file inventory relative to the uploaded archive.
