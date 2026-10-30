# Optional STAK library filter

This helper exposes an explicitly approved subset of an existing TorBox/Zurg library to Jellyfin. It is optional: the Android app works without it. All paths below are user-supplied configuration or generic container paths; no installation-specific settings are included.

## Behavior

STAK stores an encrypted, account-scoped approval outbox and sends approved torrent IDs/hashes and category to the authenticated helper. Configure the TorBox API key separately in the helper for the same account; the app does not send that key to this service.

The helper reads the TorBox catalog and matches complete filename/size manifests against an existing mounted source directory. It publishes symlinks for uniquely matched, explicitly approved whole torrents. Ambiguous, incomplete or missing mappings wait rather than guess. It does not read media bytes to calculate hashes, download media, delete TorBox items, execute shell commands or modify Zurg.

Revocation removes only helper-managed links. Mount/network failures retain existing links. Category selection is Movies or Series. Inclusion of a torrent includes all matching media files in that torrent; this is not per-episode approval within a pack.

## Configure

1. Copy `.env.example` to a private `.env` file and replace every placeholder. Never commit or share the completed file.
2. Set `ZURG_SOURCE_DIR` to the existing release directory of a healthy mounted Zurg filesystem. Set `ZURG_MOUNT_DIR` to its parent mount for the Jellyfin example. Do not create an empty substitute if the mount is missing.
3. Choose private persistent directories for `STAK_LIBRARY_DIR` and `STAK_STATE_DIR`; create them with write access for the configured numeric UID/GID. The Jellyfin process needs read/traversal access to published links and the original source mount. State should remain private.
4. Generate a unique access token of at least 32 characters and configure your TorBox account key. Optional Jellyfin API credentials allow path verification and explicitly enabled scanning.
5. Run `docker compose config --quiet`, then `docker compose up -d --build`. The service is read-only apart from its explicit output/state mounts, drops capabilities and runs unprivileged.
6. Connect STAK at Library → Zurg → Library access using your reachable helper endpoint and its access token.

The default bind is loopback for use behind an existing secured proxy. Access from a phone requires an explicitly configured trusted LAN/VPN interface or properly secured HTTPS. Do not publish this service to the open internet or enable unrestricted remote controls.

## Jellyfin integration

Jellyfin must scan the filtered Movies/Shows tree instead of the entire source library. Saving approvals in the app alone does not change Jellyfin's configured library folders. `jellyfin-volumes.txt` shows a variable-based example. Supply the same host path variables privately in the Jellyfin deployment environment and verify the paths before changing its volumes.

The standard container paths are `/media/movies`, `/media/shows` and `/media/zurg`; the helper uses `/source`, `/library` and `/state`. If a deployment uses different container paths, adjust the helper's corresponding `JELLYFIN_*` variables consistently. Container-visible symlink targets must resolve from Jellyfin before scanning. The published marker is required before the helper reports verified filtered paths. WebDAV reachability alone is not filesystem mount proof.

Library scanning is opt-in (`SCAN_ON_CHANGE=false` by default). Authentication, unique source mapping, new scan completion and successful Jellyfin indexing are separate states. No automatic scan/indexing success is inferred from a successful HTTP response.

## Development

`python -m unittest -v test_filter` uses synthetic temporary files for matching, ambiguity, revocation, race, authentication, path-escape, marker and no-media-read checks. The existing implementation relies on mounted filesystem access, not undocumented Zurg control APIs. Verify compatibility and FUSE permissions for the actual deployment separately.

The metadata HTTP client identifies itself as STAK-Library-Filter and preserves authentication and redirect restrictions. Preserve that identification when changing its network implementation.
