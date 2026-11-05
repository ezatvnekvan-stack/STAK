# Current backend: STAK Server 1.2.2 — automatic folders

User requested simple automatic organization of all approved movies and series, including legacy entries without IMDb/TMDB ids. The identity requirement now applies to metadata/acquisition identity, not the ability to make tidy approved-media folders. Common release/domain prefixes are cleaned; episodes across torrents share season folders; optional years are consistent. Existing provider IDs, cached-only acquisition guards, approval/account isolation, original files and Jellyfin12 authentication are preserved.

302 Linux offline tests passed in 19.51s. Tests cover the owner's Yaga/UIndex, Nocturne, Carrie, Common Side Effects and IT naming patterns. Real production-image fixture test: two approved unidentified/category-unknown torrents automatically published into one Yaga/Season01 directory, source files intact, graceful exit0. No live owner account calls in development tests.

Artifact: distribution/STAK-Server-1.2.2.tar.gz
SHA256: 38e73e095af5803646f67631aa08776d293e4c5e3a7a2c012b553c67c3350851
Android remains1.1.1/code59 unchanged. Owner is running1.2.1 with paths verified and six available episodes;1.2.2 deployment and final filesystem verification remain pending. Apply via sh update.sh in the existing server directory; no new pairing, keys, APK or repeated migration.

## Earlier delivery record

# Current backend patch: STAK Server 1.2.1

The owner's unified 1.2.0 deployment is live: six VisionQuest episodes are available in Jellyfin. Library path verification is blocked by HTTP401 with the legacy X-Emby-Token header. Fixed verification and automatic scan to use supported Authorization: MediaBrowser Token authentication, matching the watcher.

279 offline Linux tests passed, including real loopback 401/200 verification and scan authentication. Existing settings/keys/databases and Android 1.1.1/code59 remain unchanged. Deliver distribution/STAK-Server-1.2.1.tar.gz and use sh update.sh in the existing server directory. No repeated migration required. Live 1.2.1 check is pending.

## Previous delivery record

# Current delivery: STAK Server 1.2.0 + Android 1.1.1/code59

Implemented and packaged:
- One STAK Server container/process for cached-only series automation and approved movie/show organization.
- Direct internal approval handoff; common identity-based series/season folders and movie folders; original media untouched.
- Existing watcher/filter state migration, private SQLite WAL backups, Jellyfin config backup, reviewed layout plan, rollback preserving latest ledger.
- Existing ports/tokens supported during migration; new shared app pairing uses one address/token.
- Server-side approvals imported into Android; exact title identity editing through a metadata-only endpoint, preserving revocation.
- Existing TorBox acquisitions retained; obsolete Jellyfin-derived ids are rechecked after layout changes without duplicate acquisition.

Verification:
- 272 offline Linux Python tests passed.
- 453 debug + 453 preview unit tests passed.
- Five relevant emulator UI tests passed; final dialog layout also passed its targeted repeat and was visually inspected.
- Final production image upgrade/health/graceful shutdown verified with synthetic data, network disabled, exit0.
- Signed APK package/certificate/cloud config verified. No owner live acquisition was used in tests.

Delivery:
- distribution/STAK-Server-1.2.0.tar.gz, SHA256 7c66c1c0c414323d873d5959c1f2746a7dc383f9164bf09a2fbc0fa866df39ff
- distribution/STAK-current.apk, 1.1.1/code59, SHA256 fb395c072c3fd594d7e89b18f4895b2384b80f3c2fd352bf4de02b215c43342c
- Installation and limitations: deployment/stak-server/README.md

Not yet done on the owner's devices: Linux cutover, live TorBox/Jellyfin validation and physical-phone APK installation. Existing service is not modified remotely by this delivery. Jellyfin can assign new item ids when paths change; watched-data reassociation is not guaranteed, so a full config backup is created before cutover. The app's read-only tracking policy is preserved.

## Historical implementation ledger

## Watcher 1.1.4 - TorBox acknowledgment, reconciliation and known-file reuse

218 offline Linux tests passed. Accept documented torrent_id; schedule pending reconciliation without the six-hour idle delay; resume filter publication immediately after verified account recovery. Reuse only previously watcher-owned exact hashes with unambiguous account id and actual episode-video proof. Foreign account torrents and revoked filter approvals remain protected. Archive: distribution/STAK-watcher-update-1.1.4.tar.gz. Owner live install/publication pending.

Android code59 approval-import patch is in source and both unit suites passed; no new APK exported yet. Owner clarified the immediate issue is server publication, so deliver watcher first. Installed app code58 is sufficient for this server fix.

## Watcher 1.1.3 - Torznab magnet enclosure correction (2026-11-03)

Fixed magnet enclosure URLs being passed to HTTP fetch (Invalid HTTP endpoint). Preserves original private HTTP torrent metadata and cached-only consent guards. Safe FetchFailure event details retained. Linux offline suite: 201 passed; no live owner account mutations tested. Archive: distribution/STAK-watcher-update-1.1.3.tar.gz. Android APK unchanged. Owner must install the server update.

## TorBox cached-response correction — watcher 1.1.2 (2026-11-03)

The cache parser accepts documented requested hash/name/size records without a cached flag. Explicit false/invalid flags and mismatched hashes remain rejected. 195 Linux tests pass, including both real-parser cache guards in a fixture pipeline. The mode-600 host-file permission fix is retained. Owner must apply distribution/STAK-watcher-update-1.1.2.tar.gz; no authenticated live owner TorBox test was performed.

## Pending-sync fix + watcher reliability — app code 58 / watcher 1.1.1 (2026-11-03)

DONE in the active source and signed APK. The client compares semantic values:
strict 4K wait=0, numeric provider identities, normalized season sets. Existing
matching pending saves recover by read-only refresh. Android: 441 tests per
variant and 3 emulator UI tests passed; original signing/cloud preserved.

Server HTTP saves now persist and acknowledge before any external request.
Metadata discovery is durable, hourly, and retryable. Acquisition has a durable
pre-send reservation, one worker, account binding, final cache checks and recovery
of missing approvals. Corrected the Cinemeta URL, Torznab endpoint/episode queries,
filter scope hashing, private torrent handling and publication/index polling.

181 Linux Python tests passed without external network. Actual 1.1.0→1.1.1
fixture-volume upgrade and restart preserved a rule and episode history; second
writer rejected; clean shutdown. See deployment/stak-watch/VERIFICATION.md.

Live owner deployment is NOT updated from this session: no trusted/authenticated
SSH session was available. Apply distribution/STAK-watcher-update-1.1.1.tar.gz
with the included update.sh; it preserves configuration/data and verifies a backup.

## Auto-add overview and acknowledgement fixes — code 57 (2026-11-03)

DONE in the active source and signed APK. Library → Auto-add replaces the Series
bottom tab; Details shows active/paused/pending state. Human-readable per-episode
states, compact rows, filters, quality defaults and Nothing motion are implemented.
Pairing now verifies a protected endpoint; stop/edit acknowledgements stay honest
across connection failures and passive restore. See AUTOADD-UX.md for exact scope.

Verified: 436 JVM tests per variant (debug and preview), 3 isolated emulator UI
tests, inspected fixture screenshots, optimized signed APK with cloud configuration.
No live TorBox acquisition, owner-phone changes or Linux service deployment.
Install distribution/STAK-current.apk over the existing app; no watcher update needed.

# STAK Auto-add development status

Last updated: 2026-11-02 (Phases 3–6 complete; + Phase 6b UI rework: "Tracking" UI + change-pairing fix, signed 1.1.0 APK code 52 exported)

## Phase 6b — UI rework "Tracking" + re-pair fix (1.1.0, code 52) — DONE (2026-11-02)

User-driven follow-up on the live deployment (watcher accepted a rule save; token
mismatch exposed the re-pairing gap):

- **Fixed bug:** once paired, there was no way to change the watcher URL or token.
  The sheet now shows a **"Change pairing"** button also in the paired state (URL +
  token fields + Test connection + Save pairing + Keep current pairing).
- **Naming:** AUTO-ADD → **TRACKING** everywhere user-facing (sheet title, buttons
  "Confirm tracking"/"Stop tracking", drag-handle accessibility labels).
- **Entry point moved:** the tracking entry is no longer in the bottom dock; it is a
  prominent **"TRACK SERIES"** outlined row directly below the management panel
  (hero area) on the title screen. The bottom dock keeps FIND SOURCES only.
- **Simpler sheet:** Dolby Vision and Atmos selects removed from the UI (the stored
  selections are still sent to the watcher unchanged, contract preserved); Language,
  size limit and check frequency moved behind a collapsible **"More options"** row.
- Full regression at code 52 green (both variant unit suites + assemblePreview).
- Signed build verified: `app.stak.nativeapp.parity`, versionCode **52**, versionName
  1.1.0, certificate SHA-256 `bd9c33eb…0c430` (original identity).
  APK SHA-256: `d256225711ef2b54e9555e5b2db24d1312f3042c8b562765eaff97e59c221a74`.
- Install file: `${PROJECT_ROOT}\distribution\STAK-current.apk`.

Server state (user's Linux laptop, stak-watch on 192.168.0.5:8055): container runs
(Docker 29.9.0), health 200, /v1/status 200 after .env was fixed from a merged
single-line mangle. Pending on-device/live re-check after this UI rework: pairing
flow with the corrected token and the first confirmed rule on the sheet.

Last updated: 2026-11-02 (Phase 7 live verification PASSED — watcher on the user's Linux laptop, first rule confirmed, 10 episodes tracked, no errors)

## Phase 7 — Live on-device / on-server verification — DONE (2026-11-02)

First real end-to-end run against the user's own deployment (Linux laptop
192.168.0.5, stak-watch in Docker, Jellyfin + stak-library-filter on the same
box):

- Watcher container: **Up (healthy)**, Docker 29.9.0, Compose v5.6.0, Python 3.12.
- Health + token-gated `/v1/status` → **HTTP 200**.
- First rule pushed from the phone (code-54 app, TRACKING sheet) → stored as
  `confirmed: true`, `paused: false`, `onlyNewEpisodes: true`, HDR mandatory,
  2160p only, WEB-DL, watched season 1.
- Watcher already processed the series: **10 episode rows** created, all in
  `waiting_release` — proves the scheduler and provider search ran end to end.
- No `episode_error` / `ValueError` lines in the container log.
- App side: chip state **"Confirmed by watcher" + "Active"**, save result line
  **"Saved on the watcher."**, status line **"10 episodes · 10 waiting_release"**.
- Cached-only preserved: no TorBox add has been triggered by this verification;
  the rule pipeline only adds releases that are cached (and approved by the filter).
- The re-pairing gap is closed: **"Change pairing"** works in the paired state.
- Token consistency is now guaranteed by the laptop `.env` (single source of truth)
  plus `docker compose up -d --force-recreate` on change.

Verified locally on this machine (tests/build) vs. still not exercised here:
- Not exercised here: physical-device UI layout beyond compile/runtime checks,
  instrumented UI tests (`DetailsLayoutUiTest`, `NothingSharedArtworkTest`,
  `PreviewTenUiTest`), VPN/AP isolation states, and live TorBox add (intentionally
  left to the organic cached-release flow — no manual TorBox mutation).

## Phase 6c — compact tracking UX + visible save results (1.1.0, code 53) — DONE (2026-11-02)

Second UX pass after on-device feedback ("Save changes did nothing visible"):

- **Diagnosability fix:** the save handler now catches `Throwable` (not just
  `Exception`), shows "Saving…" on the button while running, and always prints the
  outcome under the button: "Saved on the watcher.", "Saved on this device. <reason>"
  or "Could not save. <reason>". No silent failures remain.
- **Compact entry:** the big TRACK SERIES row is replaced by a compact outlined
  button ("TRACK SERIES" with the radio glyph) under the management panel.
- **Compact sheet:** TRACKING title, one status line, editable pairing (also when
  paired: Change pairing / Test connection / Save / Keep current), then Mode,
  Episodes (only new, resolution, optional 4K wait), Release types and the save
  button. Everything else (watched seasons, HDR, language, size limit, check
  frequency) lives behind one "More options" row (collapsed by default).
- Dolby Vision / Atmos stay out of the UI (stored values still sent unchanged).
- Regression green at code 53 (both unit suites + assemblePreview); signed export
  verified: applicationId `app.stak.nativeapp.parity`, versionCode 53, original
  certificate `bd9c33eb…0c430`.
  APK SHA-256: `4c42c7636868c6a2fa0ec096529e102b4eefd91574030b7dc86737b89f1a8343`.
- Install file: `${PROJECT_ROOT}\distribution\STAK-current.apk`.

Not yet verified on the phone: the new layout's rendering and the save result line
under real network conditions (user device check pending).

## Phase 0 — Roll back Find Sources redesign to 1.0.2 — DONE

- The previous Find Sources redesign is fully reverted. Workspace verified against
  `HANDOFF-MANIFEST.json`: 344 of 345 files are byte-exact 1.0.2 (SHA-256).
- Recovery method: OpenCode message DB (`C:\Users\Zsolt\.local\share\opencode\opencode.db`,
  copied to `C:\Users\Zsolt\AppData\Local\Temp\opencode\ocopy\`) contained full, untruncated
  1.0.2 read outputs of all 6 redesigned files. They were extracted, re-serialized, and
  verified byte-exact against the manifest before restore.
- Restored byte-exact to 1.0.2:
  - `app/src/main/java/app/stak/nativeapp/ui/screens/DetailChrome.kt`
  - `app/src/main/java/app/stak/nativeapp/ui/screens/FindSourcesSheet.kt`
  - `app/src/main/java/app/stak/nativeapp/ui/screens/TitleScreen.kt`
  - `app/src/androidTest/java/app/stak/nativeapp/DetailsLayoutUiTest.kt`
  - `app/src/androidTest/java/app/stak/nativeapp/NothingSharedArtworkTest.kt`
  - `app/src/androidTest/java/app/stak/nativeapp/PreviewTenUiTest.kt`
- Deleted redesign-only files: `ui/components/SourceSheetChrome.kt`, `ui/components/SourceReleaseCard.kt`.
- `app/build.gradle.kts`: only difference from 1.0.2 is intentional — `versionCode 50`,
  `versionName "1.1.0"` (release target ≥ 50 for installable update). All other content byte-identical.
- Stale redesign artifacts still on disk, to be replaced at delivery time (not installed yet):
  `distribution/STAK-1.1.0-preview.apk` (redesign APK — do NOT install), `delivery/STAK-source.zip`.
- Verification: `.\gradlew.bat :app:testDebugUnitTest :app:assemblePreview` → BUILD SUCCESSFUL
  (1m33s). Compile, unit tests, R8, packaging all pass on the reverted state.
- Not yet verified: physical device / instrumented tests on device.

## Phase 1 — Current-system mapping — DONE (2026-10-31)

Key integration facts (source files named in repo):
- Find Sources pipeline: `data/SourceRepository.kt` (`parseSourceRelease`, `filteredSources`,
  `SourceRepository.cached` → TorBox `checkcached` 80-hash batches, `addCachedSource` = fresh
  re-confirm immediately before `createtorrent` with `add_only_if_cached=true`), `data/SourceSearch.kt`
  (addon + Torznab merge, chunked 3), `data/AddonSources.kt` (Stremio `/stream/series/imdb:s:e.json`),
  `data/TorznabSources.kt` (caps→tvsearch imdbid, metadata ≤5 MiB, `sourceIdentity`), `data/CachedSources.kt`
  (credential-scoped metadata cache, Semaphore(3)), `data/SourceQuality.kt` (rank key: clean >
  resolution > tier REMUX/BluRay/WEB-DL/broadcast > DV>HDR > lossless > atmos > cached > HUN).
- Series metadata: `data/DirectTitleMetadata.kt` (TMDb per-season episode lists with `air_date`),
  `data/CalendarRepository.kt` (upcoming windows), `data/JellyfinPlayback.kt`
  (`resolve()` matches episode by ParentIndexNumber/IndexNumber; read-only tracking).
- Approvals: `data/LibraryApprovals.kt` → filter `POST /v1/approvals` `{id, scope, hash?, name, type, approved}`,
  scope = sha256("torbox:<torbox key>"), auth `Bearer STAK_ACCESS_TOKEN`; filter reconciles TorBox
  `mylist?bypass_cache=true`, requires `download_finished`, publishes symlinks only; whole-torrent granularity.
- Persistence/backup: Room v4; `SettingsStore.backupValues()/backupSecrets()` include new properties
  automatically (`CloudSnapshot.cloudBackupKey` exclusion list); snapshot JSON `schema:4` hardcoded
  in capture()/validateCloudSnapshot() (both sides must be bumped together). Restore is purely
  passive (never replays adds/writes) — auto-add rules must follow that contract.
- Docker filter reference: `deployment/stak-library-filter/filter_server.py` (env config, marker
  guard, path-escape checks, optional Jellyfin `/Library/Refresh`), `test_filter.py` style.
- Watcher design decision: the watcher is a new, self-contained Python service
  `deployment/stak-watch/` (stdlib-only core + HTTP), persistent SQLite (rules/episodes/torrents/
  search-cache/audit events), provider config (addon manifests + Torznab) supplied as config
  (sample config committed secrets-free, real values from .env, never printed), TorBox
  cached-only enforced server-side (checkcached re-confirm then createtorrent
  add_only_if_cached=true), STAK-filter approvals via the existing /v1/approvals protocol
  (type=series), no season packs ever auto-added (episode releases only), no quality swap in v1,
  no media downloads (Zurg mount only).

Statuses served by the watcher per episode: waiting_release, waiting_release_match,
 Waiting_for_TorBox_cache(waiting_torbox_cache), added_torbox, waiting_library,
 available_jellyfin, paused, connection_unavailable.

## Phase 2 — Filter/rank/identify/dedup core + tests — DONE (2026-10-31)

`deployment/stak-watch/` created:
- `core/traits.py` — release parsing (faithful port of app semantics; HDR10+ variant fix; bare
  TS added to damaged markers; Hungarian SUBTITLE markers never count as Hungarian AUDIO;
  MULTI audio is honest "unknown").
- `core/rules.py` — WatchRule (resolution modes 2160-only/1080-only/2160-preferred + wait hours,
  release types, HDR/DV/Atmos modes mandatory/preferred/any, language modes
  any/hun_mandatory/hun_preferred/original, max episode GB, check minutes 15..720 default 45,
  watched seasons + specials default unwatched, only-new vs include-missing, pause); JSON
  round-trip; contradictory-condition warnings (`size_cap_small_for_4k`, `hdr_and_dv`,
  `hun_atmos_rare`, `four_k_webrip`, `atmos_size`) with user-readable messages.
- `core/episode.py` — stable episode keys, air-date window (calendar-date only, unknown air date
  never "released"), only_new_episodes window, paused guard, fallback cutoff math.
- `core/matching.py` — identification (episode marker must match; season packs and
  unmarked collections never auto-added), mandatory filtering with honest reject reasons
  (unknown traits never verified), 2160-only never falls back, wait-window fallback for
  2160-preferred, cached-only mandatory, size cap, ranking (app order + preference bonus).
- `core/dedup.py` — AddLedger decisions (already_added/identical_state/reuse/reconcile_first/add),
  provider-state reconciliation-first (no blind re-add on ambiguous TorBox answers),
  unconfirmed-add rollback on reconcile, no quality swap in v1.
- Tests: 85 assertions in 5 pytest files under `tests/`, run in `python:3.12-alpine` container via
  Docker: **65 passed** (test_traits, test_rules, test_matching, test_episode, test_dedup).
- Run: `docker run --rm -v .:/w -w //w -e TZ=UTC python:3.12-alpine sh -c "pip install -q pytest && python -m pytest tests -q"`

## Phase 3 — Docker watcher + authenticated API with provider fixtures — DONE (2026-11-02)

Service layer written under `deployment/stak-watch/`:

- `core/store.py` — SQLite persistent store (WAL, FULL sync, single conn + RLock);
  tables rules/episodes/torrents/search_cache/events. Added `series_jellyfin` column
  (migration at boot) so the Jellyfin series id is resolved once per episode and
  persisted; added `torrents_unconfirmed()` used by startup reconciliation.
- `core/service.py` — `WatchService` + stdlib HTTP API (`ThreadingHTTPServer`,
  Bearer `STAK_WATCHER_TOKEN`):
  - `GET /health` (no auth), `GET /v1/status`, `GET|POST /v1/rules`, rule pause/resume,
    `DELETE /v1/rules/{id}` (pause + keep history), `GET /v1/episodes`, `/v1/events`,
    `POST /v1/reconcile`.
  - `POST /v1/rules` refuses payloads without explicit auto-add consent
    (`confirm`/`confirmed`) — watchlisting alone never enables automation; invalid
    rules 400; implausible combos return `warn_implausible` issues for the UI.
  - Episode rows synced from the official Cinemeta series meta (rules without an
    IMDb id are rejected with an explanation, never a title guess).
  - Serialized scheduler: single thread processes due episodes gently
    (max 6/tick, 2 s pacing), unexpected episode errors → `connection_unavailable`
    + backoff instead of a crash; startup + 6-hourly ledger reconciliation.
- `core/providers.py` — addon + Torznab searches now return `(candidates, warnings,
  failures)` and raise `ProviderError` when every provider failed (network outage is
  never a false negative cache). Fixed addon stream URL derivation (manifest.json
  segment stripped), Torznab imdb id normalization.
- `core/providers.py` negative cache per provider; stale entries revisited (20h+).
- `core/torbox.py` — fixed `checkcached` to exclude `cached:false` entries in the
  object-response shape too (defensive honesty), kept list/string shapes.
- `core/pipeline.py` — `_post_approval` implemented (filter `type=series` approval with
  the account-bound scope hash); `_publication_stage` resolves the Jellyfin series
  id once (read-only `find_series_base`) before episode verification; optional scan
  request only when `STAK_JELLYFIN_SCAN=1` and the series id is known.
- `core/fetch_client.py` — stdlib urllib fetch client implementing the `fetch()`
  contract used by all wrappers (HTTP errors return status + body, callers decide).
- Tests: new fixture infrastructure (`tests/conftest.py` — fake fetch client with
  routed responses, fake TorBox/filter/Jellyfin doubles) + new test files
  `test_providers.py`, `test_torbox.py`, `test_pipeline.py`, `test_service.py`
  covering: parse honesty, all-addons-failed → outage, wrong-imdb torznab items
  never matched, checkcached shapes, pagination, cached-only add body assertion,
  unconfirmed-add paths, noisy-but-unrelated account state never mutated,
  happy path with verified library gating, revoke → no re-add, missing mount → no
  re-add, negative cache skip + TTL expiry re-check, HTTP API auth/consent/CRUD.
  Suite: **118 passed** in `python:3.12-alpine`.
- Docker packaging added: `Dockerfile` (python:3.12-alpine, stdlib-only, /data
  volume, /health healthcheck), `compose.yaml` (host networking, persistent
  ./data, torznab file mounted read-only), `.env.example` (template only),
  `README.md` (install/update steps, API table, guarantees, test command).

Still open in Phase 3: none blocking — remaining verification is on-server only
(app pairing + live-stream E2E is deliberately NOT tested against the live
TorBox account; see constraints).

## Phase 4 — Android settings UI + status sync — DONE (2026-11-02, server-side flow not yet verified live)

Data layer (`app/src/main/java/app/stak/nativeapp/data/WatchRules.kt`, new):

- `WatcherConnection` (url + token + enabled; `toString()` never reveals credentials),
  `WatchModes` (resolution/trait/release/language enums + bounds — 1:1 with the watcher's
  `WatchRule` validation), `WatchRuleModel`, `WatchRuleStatus`, `WatcherDocument`.
- `WatcherCodec` — strict encode/decode of the document (schema guards, corrupted JSON →
  IOException "Saved watcher rules could not be read…" and no overwrite). `payloadFor(rule, consent)`
  builds the exact `POST /v1/rules` payload (ruleId, series{imdb,tmdb,tvdb,jellyfin,name},
  enabled, paused, createdOn, watchedSeasons, includeSpecials, onlyNewEpisodes,
  resolutionMode, resolutionWaitHours, releaseTypes, hdrMode, dvMode, atmosMode, languageMode,
  maxEpisodeGb, checkMinutes + `confirm:true`, `confirmed:true`) and refuses without explicit
  consent — watchlisting alone must never enable automation.
- `watchRuleIdFor(seriesKey)` — deterministic `watch_(imdb|tmdb|tvdb|jf)_<id>_<sha256-12>`;
  stable across restarts/backup-restore; used verbatim as the watcher's rule_id.
- `watchEndpoint()` URL guard (HTTPS or private LAN/VPN only, no embedded credentials/query);
  `watchToken()` guard; `HttpWatcherTransport` (Bearer token, `Connection: close`, 4 MiB cap,
  user-readable failure strings, token never logged); `WatcherClient` typed ops (health /
  get / saveRule / pauseRule / resumeRule / removeRule / episodes) with injectable transport.
- `WatchRules` facade: `saveRule` (persists as `saved_local`, pushes only when paired with
  explicit consent, marks `server_confirmed` only after the watcher answers 2xx),
  `turnOff` (removes locally; watcher DELETE pauses episodes and keeps history),
  `setPaused` (POST pause/resume endpoints), `refresh` (read-only status pull per rule:
  GET rule record + episode rows → per-rule counters cache), `probe` (pairing), `saveConnection`
  (validated URL+token storage). All facade ops accept injectable persistence + client factory
  for offline tests.
- Persistence: `watcher_rules_v1` in SECURE storage (`SettingsStore.watcherRulesJson`,
  `saveWatcherRulesChecked`) — same pattern as library approvals, so rules + connection join
  the encrypted cloud backup via `backupSecrets()`; no Room schema change.
- `watch_status_cache` prefs key is written to transient cloud-cache slot and is EXCLUDED
  from `cloudBackupKey` (server state is re-fetched; backups never carry stale watcher data).

UI (`ui/screens/AutoAddSheet.kt`, wired via `DetailChrome.DetailSourceDock` + `TitleScreen`):

- Series Details gets a second outline button "AUTO-ADD" under FIND SOURCES (series items
  only; films show nothing). Sheet mirrors FindSourcesSheet (FlexibleBottomSheet, glass,
  Nothing outlines, Doto/SpaceMono/Hanken typography, testTag-free layout in English).
- Sheet sections: rule state badges ("Saved on device" vs "Confirmed by watcher" + Active/Paused),
  per-rule episode-row counters + fetch time, watcher pairing block (URL + token fields,
  Test connection, Save pairing — probe verifies the endpoint answers like stak-watch),
  ON/PAUSED/OFF mode chips, watched-season chips (+ Specials), only-new vs include-all,
  resolution mode + 4K wait, release types, HDR/DV/Atmos modes, language mode, per-episode
  size cap, check-interval selector, explicit "Confirm auto-add" / "Save changes" button
  (the consent act), and a named turn-off confirmation dialog ("Turn off auto-add for
  <series>?" — watcher pauses episodes, keeps history).
- Status refresh runs passively (read-only GETs) when the sheet opens; no TitleViewModel
  changes; nothing ever writes watched state or replays server jobs.

Contract cross-check: app payload keys verified line-by-line against
`deployment/stak-watch/core/rules.py` (`ruleId`, nested `series{imdb,tmdb,tvdb,jellyfin,name}`,
`confirm`/`confirmed` gate at `service.py` POST /v1/rules).

Tests: new `app/src/test/java/app/stak/nativeapp/data/WatchRulesTest.kt` — 15 tests, all green:
rule-id stability/prefix, exact payload key set + explicit consent flags, consent gate refuses
before any transport call, save→server_confirmed via fake transport (path/method/body asserts),
unpaired stays `saved_local`, turnOff → DELETE path, pause/resume endpoint paths, refresh maps
rule record + episode rows into the status cache, pure status-row parser, codec round-trip,
corrupt/garbage decode refusal, endpoint guard (HTTPS/private-LAN only, no credentials/query),
token guard, `cloudBackupKey("watch_status_cache")==false`.

Verification: `.\gradlew.bat :app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview`
→ BUILD SUCCESSFUL (~1m13s), both variants 0 failures 0 errors (834+ assertions across 42 suites;
`build/test-results` XML has zero `<failure>` entries). afterEachCheck: versionName 1.1.0
(versionCode was 50 at this point; bumped to 51 in Phase 6).

Not yet verified (Phase 5/6 scope): on-server pairing with a real stak-watch instance,
live rule push/pause/delete round trips, instrumented UI on a device, and any real TorBox
interaction (deliberately never tested against the live account).

## Phase 5 — Full simulated flow + regression — DONE (2026-11-02)

Simulated end-to-end cooperation, isolated fixtures only (never the live TorBox account):

- **Watcher API ↔ Android client round trips** (`app/src/test/.../data/WatcherRoundTripTest.kt`,
  real OkHttp `HttpWatcherTransport` over a loopback stub on 127.0.0.1 that re-implements
  stak-watch's exact API habits — Bearer `STAK_WATCHER_TOKEN`, consent gate with HTTP 400
  without `confirm`/`confirmed`, canonical (unwrapped) rule answers, DELETE = pause +
  history kept). 7 tests green:
  - health handshake probe; full lifecycle: POST /v1/rules 201 → `server_confirmed` →
    status refresh (unwrapped rule + episode-row counters) → pause/resume endpoint paths →
    DELETE (server history rows survive, local rule removed);
  - connection dropout: local rule keeps state, user-readable issue, offline saves stay
    `saved_local`; saved rules are NOT re-posted;
  - app/watcher restart + restore: deterministic rule ids, passive refresh re-confirms by
    reading only; **zero duplicate POSTs / zero duplicate server rules** after restore;
  - watcher store lost (fresh install): restore stays local-only, no re-save;
  - wrong token → server 401 mapped to a readable issue, rule stays unconfirmed.
- Contract mismatches found and fixed during this phase:
  - `refresh()` now handles the service's canonical (unwrapped) `GET /v1/rules/{id}` answer
    as well as the wrapped pause/resume/POST answers;
  - empty-body POSTs (pause/resume) now carry an explicit empty request body (OkHttp);
  - 404 messaging no longer claims a wrong base URL when the rule is simply unknown;
  - `watchRuleError` logging is wrapped so JVM unit tests never crash on unmocked
    `android.util.Log` (device logging unchanged).
- Watcher regression re-run in Docker: `python:3.12-alpine` pytest — **118 passed**.
- Cached-only preserved by construction: the app only ever calls `/health` and `/v1/*`
  paths (asserted in every round-trip test) and has no TorBox add/delete code path; the
  server-side `add_only_if_cached=true` assertions remain covered by the watcher pytest
  suite (`test_pipeline.py`, `test_torbox.py`).
- Full Android regression at versionCode 51:
  `:app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview` → BUILD SUCCESSFUL
  (1m18s), both variants 0 failures 0 errors (44 suites incl. WatchRulesTest 15 +
  WatcherRoundTripTest 7).

## Phase 6 — Signed APK — DONE (2026-11-02)

- `versionCode` bumped 50 → **51** (`versionName "1.1.0"` unchanged) in `app/build.gradle.kts`.
- Built + exported with `.\build-update.ps1` (injects cloud configuration, verifies the
  original signing identity, refuses a wrong cert or older versions; no tests re-run
  because the full suite was green immediately before).
- Exported/verified APK:
  - **Install file:** `${PROJECT_ROOT}\distribution\STAK-current.apk`
    (the stale redesign APK `STAK-1.1.0-preview.apk` is no longer in the folder; install
    only `STAK-current.apk`).
  - `current-version.json`: applicationId `app.stak.nativeapp.parity`, versionName 1.1.0,
    versionCode 51.
  - Independent check with `aapt dump badging`:
    `name='app.stak.nativeapp.parity' versionCode='51' versionName='1.1.0'` — original
    application id kept, so upgrading from 1.0.2 (code 49) keeps all app data.
  - `apksigner verify --print-certs`: certificate SHA-256
    `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430` = the original
    signing identity (hardcoded in build-update.ps1).
  - APK SHA-256: `9b0c16354378f5f56be5f8b52a6fa1f2ad9efcd16619761ea8fbc8ed138c6fd3`
    (also in `distribution/current-version.json`).

Verified on this machine (tests/build only) vs. still requires on-device / live checks:

- Verified here: all unit + round-trip + watcher pytest results above; signed APK,
  version, application id, signature identity.
- NOT verified on a physical phone yet: sheet rendering/UX on real hardware, install
  upgrade from 1.0.2, instrumented UI tests (`DetailsLayoutUiTest`, `NothingSharedArtworkTest`,
  `PreviewTenUiTest`).
- NOT verified against live services (deliberately, by constraint): pairing with the real
  stak-watch instance on the server, live rule push/pause/delete, switched-off/VPN states,
  real Jellyfin tracking interplay, and anything touching the live TorBox account.

## Open questions / constraints carried forward
- Series-only automation authorized by user brief (overrides older handoff ban); films never automated.
- Existing library filter stays the library publisher; no download to phone/laptop storage (Zurg mount only).
- Jellyfin playback tracking remains Jellyfin-authoritative, read-only.
- Never run auto add/delete tests against the live user TorBox account.
- Keep app.stak.nativeapp.parity, original signing key, cloud deployment; UI English; Nothing style.
## Phase 6e — Central tracking page + compact popup + animations (1.1.0, code 56) — DONE (2026-11-02)

User-driven: move watcher pairing out of the TRACKING popup into a dedicated
top-level page, make the per-series popup compact (app GlassDialog style), add
animations/indicators, and build the central tracking list.

- New bottom-nav destination "Series" (id `series`, icon radio) opens a
  dedicated `TrackingSettings` screen: watcher pairing (URL + token, Test
  connection, Save / Change pairing / Keep current) + the central list of all
  tracked series with state chips, episode counters, Pause/Resume and Turn off
  (confirm dialog). Opened also from Settings -> "Series tracking".
- TRACKING popup is now a compact GlassDialog (not a full bottom sheet): Mode,
  Episodes (only new, resolution, optional 4K wait), Release types, Save, with
  Watched seasons / HDR / language / size / check frequency behind one "More
  options" row. Pairing moved entirely to the central page; the popup shows
  paired URL or an "Open settings" button.
- Animations/indicators: `nothingReveal` on the TRACK SERIES button and
  tracking rows, existing press-motion + animated stroke on buttons, animated
  bottom-bar indicator (already present), status chips.
- The TRACK SERIES button is now a compact outlined button (not a large row).
- Full regression green at code 56 (both variant unit suites + assemblePreview);
  signed export verified: applicationId `app.stak.nativeapp.parity`, versionCode
  56, versionName 1.1.0, certificate `bd9c33eb...0c430` (original).
  APK SHA-256: `c22719a0829f222fcd83e256a65038e8c6c8cb015d8b54a249ce8548e6bb874f`.
- Install file: `${PROJECT_ROOT}\distribution\STAK-current.apk`.

Note on naming: the tab is labeled "Series" (not "Sonarr") to avoid implying
Sonarr integration, which is out of scope. Rename to "Sonarr" is a one-line
change if requested.

## Phase 7 — Live on-device / on-server verification — DONE (2026-11-02)

First real end-to-end run against the user's own deployment (Linux laptop
192.168.0.5, stak-watch in Docker, Jellyfin + stak-library-filter on the same
box): watcher Up (healthy); /v1/rules + /v1/status HTTP 200; first rule pushed
from the phone stored as confirmed=true, paused=false; watcher processed the
series (10 episode rows, all waiting_release); no episode_error/ValueError in
logs; app chip "Confirmed by watcher"/"Active", save result "Saved on the
watcher.", status "10 episodes · 10 waiting_release". Cached-only preserved (no
TorBox add triggered by verification). Re-pairing gap closed via "Change
pairing". Token consistency guaranteed by laptop .env + force-recreate.
