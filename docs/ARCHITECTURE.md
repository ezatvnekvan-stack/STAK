# Architecture and development guide

STAK is a standalone Kotlin/Jetpack Compose Android application. Keep the existing package structure, navigation, encrypted `SettingsStore` and Room schema/migrations. The installed application ID is `app.stak.nativeapp.parity` for the preview distribution.

## Data and services

- Jellyfin is authoritative for playback progress and current watched state. Tracking is passive: do not introduce watched/unwatched UI or replay legacy pending playback writes. Historical observations stay account-scoped and are not erased merely because an item leaves the library. Do not infer exact watch duration from a watched flag.
- Find Sources combines explicitly configured Stremio addons and Torznab indexers. Results require TorBox cache verification. Preserve exact private `.torrent` metadata, progressive bounded checks, account-scoped memory caches, fresh confirmation checks and `add_only_if_cached=true`. Do not treat playback URLs as torrent metadata.
- TorBox management supports explicit user operations with account guards, no blind retries and named delete confirmations. Search, sorting, refresh and selection must not perform mutations.
- Zurg is an existing external service. Use WebDAV for browsing; reachability does not prove Linux mount health. Do not invent control endpoints or download media to the phone/server.
- The optional Docker library filter publishes only symlinks for explicitly approved complete torrents. Preserve encrypted approval outbox, credential scope/revision isolation, unique source matching and marker-based Jellyfin path verification. Android approvals alone do not filter an unmodified Jellyfin library.

The project intentionally does not expose an internal full media player, Sonarr/Radarr/Seerr integration, quality-profile automation or a mandatory STAK backend.

## UI

Keep Doto, Space Mono and Hanken Grotesk typography, restrained red accents, rounded outlines, cinematic artwork light and glass navigation/dialogs. No dot-matrix background or pixel notches. Pause ambient motion during scrolling, modals, backgrounding and Reduced motion; avoid per-frame blur and self-referential capture.

Details chrome reserves layout space. Library uses a compact selector plus permission-gated real scan progress on Jellyfin only; TorBox uses counted status tabs, compact rows and long-press selection. Preserve underlying menus, collection/version navigation and partial-error retries when refining presentation.

## Verification

Use JVM tests for protocol, parsing, migrations and account/isolation rules. Use focused Android tests for navigation, read-only tracking, cache gating, confirmations, real scan-state reducers and layout geometry. Several older instrumentation tests are explicitly ignored because their manual watched controls were retired; do not restore those controls to satisfy obsolete tests.

Tests use synthetic credentials and controlled fixtures. Never run acquisition/deletion against a user's live account as an automated smoke test. Inspect rendered UI, test the optimized APK and verify encrypted-data preservation on in-place updates. Version names may restart at 1.0.0; Android version codes must continue increasing.

Private signing files, `.env`, SDK paths, generated binaries, screenshots and logs must remain untracked. Keep the official update certificate unchanged for published APKs. Public contributor builds deliberately use their local debug certificate when the official key is absent.
