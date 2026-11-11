# Developing STAK

This checkout is the current native Android source, not the retired web app. Read `AGENTS.md` and `docs/ARCHITECTURE.md` before editing. Project-local skills are under `.agents/skills`; keep their provenance and license files.

## Build and data compatibility

- Use the `preview` variant for `app.stak.nativeapp.parity`; the `release` variant has a different application ID.
- Contributor builds work without private files. Official updates require the original private signing key. Never commit it or generate a replacement while claiming update compatibility.
- Current versionCode is 84. Use 85 or higher for the next distributable update.
- Preserve encrypted settings, Room schema/migrations and cloud restore/backup behavior. Do not recreate the existing hosted deployment or blindly reapply migrations for an app update.
- Cloud URL and publishable client key are build inputs. Privileged keys, tokens, completed environment files, private addon/Torznab URLs and databases stay out of Git.

## Product invariants

- Product UI is English; preserve the existing Nothing-style typography, cinematic artwork, restrained red and reduced-motion behavior.
- Jellyfin playback tracking is read-only. Never add watched/unwatched writes or restore queued playback mutations.
- Find Sources and Auto-add are cached-only. Preserve exact private torrent metadata, explicit consent, fresh cache confirmation, account isolation and duplicate-add guards.
- Auto-add remains optional. Browsing a series, restoring a backup, or adding it to a watchlist must not enable acquisition.
- Keep named destructive confirmations and avoid blind retries after uncertain mutation outcomes.

## Validation

Use focused JVM tests for data/protocol changes and isolated device fixtures for UI behavior. Inspect rendered UI after layout changes. Record what was actually checked; emulated fixtures do not prove live Jellyfin/watcher/TorBox operation. Gradle calls made by coding agents follow `.agents/skills/gradle-run/SKILL.md`.

GitHub Actions builds with contributor signing and runs offline server tests. It does not deploy services or publish official signed updates. Keep backend runtime `.env`, `torznab.json`, `data/` and migration state private.
