# STAK Native development

Read README.md and docs/ARCHITECTURE.md first. This is the active native Kotlin/Compose project.

- Product UI stays English. Preserve Nothing-style rounded outlines, glass and cinematic backgrounds.
- Build the optimized preview variant with `./gradlew :app:assemblePreview`.
- Preserve app.stak.nativeapp.parity, encrypted credentials, Room schema/migrations and official signing identity.
- Playback tracking is Jellyfin-authoritative and read-only. Never restore manual watched/unwatched actions or pending write replay.
- Find Sources is cache-only with a fresh confirmation guard. Preserve original private torrent metadata and bounded account-scoped checks.
- No full player, required backend, Sonarr/Radarr/Seerr or quality-profile automation.
- Keep named delete confirmations, no blind mutation retries and account guards.
- Keep private keys, completed .env files, generated APKs, logs and owner-specific service details out of Git.
- Verify relevant tests and rendered UI; do not claim physical-device or live-service success from compilation alone.
