# Android 1.3.0 / code64 — Discover, Details and adaptive Continue Watching

Owner approved a combined full remake, not a minor style adjustment. Active source remains the private Desktop/STAK-OpenCode project. Optional STAK Server1.2.3 is unchanged and does not need redeployment.

## Discover

- Compact header with animated search expansion, filters, Movies/Series navigation and filled square streaming shortcuts.
- Unfiltered explore page: For tonight (explicitly a top-rated selection), genre-based mood entry points, Trending now, public community collections and Worth discovering. Lists are integrated rather than a top-level Titles/Lists navigation layer; Browse all lists retains search, saved lists, ordering and title navigation.
- Query, provider or other filters switch to an independent three-column result grid. Category/sort, active chips, bounded pagination, partial errors and retries remain available. Back returns to exploration and state holders retain each mode's scroll.
- DiscoverDeckViewModel requests at most three metadata shelves concurrently, with generation cancellation and a five-minute/six-key in-memory cache scoped to media type/region. Partial failure retains other shelves. It performs metadata GETs only, no acquisition or tracking.
- Top-rated fallback uses Cinemeta's rating catalog; trending falls back to the real public Trakt trending endpoint when TMDb is absent. Hidden-gem filtering requires TMDb rather than relabelling a generic popular fallback. Genre labels come from available provider options. No fabricated personalized scoring, release availability or streaming entitlement is shown.

## Details

- One continuous image/header with logo or title, concise facts, genres and valid provider-labelled ratings. Missing/zero TMDb scores remain absent.
- Compact top action row: original cache-only Find Sources sheet plus accessible trailer/bookmark icons. Primary source action is capped at216dp. No fixed footer or large action background box.
- Neutral library presence and actual Jellyfin quality metadata when known; existing quality dialog and movie progress remain read-only.
- Series show a separate Auto-add status row (Set up, Pending sync, Active, Paused, Stop pending) opening the existing consent-gated editor.
- Expandable three-line synopsis, outlined season selector, watched count and virtualized vertical episode rows. Watched checks, next-up/resume emphasis are presentation only; no playback writes or new acquisition paths.
- Existing episode/source sheet selection, season cancellation/retry, and saveable synopsis/season/scroll remain. Restoring the already selected season no longer resets a Continue Watching request to episode1.
- Cast, compact primary creator/director, separate full-credits dialog, wider gallery carousel, collection and similar-title rows. Person/artwork dialogs and underlying data remain available.

## Continue Watching

Card width is based on real Compose text measurement at the active font scale: title plus metadata and thumbnail/padding. It has a188dp minimum (where the viewport permits),84% viewport/336dp maximum and at most two title lines. Short and long cards differ in width; a singleton stays compact. Metadata, read-only progress, click and context menu are retained.

## Safety and verification

Encrypted preferences, Room schema, account scopes, cloud deployment, backup/restore, private torrent metadata, fresh cache confirmation, explicit Auto-add consent and Jellyfin-authoritative tracking are unchanged. Tests use isolated settings and local artwork/catalog fixtures; no live owner-account mutations.

Verification results and signed-package identity are recorded below. Physical-phone rendering/FPS and live private-service execution are not claimed.

## Final verification

- 470 debug +470 preview JVM tests,0 failures/errors on the final source.
- 20 distinct API35 UI checks passed across focused runs:8 new remake checks, existing Home motion/reduced motion, public-list interactions, progressive season cancellation/retry, Auto-add state/quality controls, and artwork-only shared navigation. The8 remake checks were repeated after the final layout polish; no failures. Retired WATCHLIST/S02 assertions were updated to the new icon/season-dropdown interactions without changing test intent.
- Inspected rendered local-fixture captures of Discover top/collections, movie Details, series header/episode rows, full credits and short/long Continue Watching cards. Artwork is synthetic test data, not a live provider/account test.
- Original-signer optimized preview build succeeded with lintVital/R8/resource shrinking and the existing cloud deployment injected by build-update.ps1.
- Signed APK installed with adb -r on API35 and cold-launched successfully. No owner-phone uninstall or account/service mutation was performed. Server1.2.3 and all backend packages remain unchanged.

Application ID: app.stak.nativeapp.parity

Version: 1.3.0 / 64

APK SHA-256: d49f88a873cd448d3d812cab7555058bca3ad73545a966188483ffb55cd0034a

Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430
