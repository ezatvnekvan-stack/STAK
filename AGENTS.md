# STAK development

Current Android source: **1.5.1 / versionCode 76**. Next distributable versionCode: **77+**. Current optional backend: **STAK Server 1.2.3**.

Read `README.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md` and `docs/SERIES-CENTER-1.4.0.md` first. This repository is the native Kotlin / Jetpack Compose project. Do not restore the retired web app or separate legacy watcher/filter implementations.

- Product UI stays English. Preserve Nothing-style Doto, Space Mono and Hanken typography, restrained red accents, rounded outlines, cinematic artwork and reduced-motion behavior. Project-local skills and licenses live under `.agents/`.
- Use `preview` for `app.stak.nativeapp.parity`. Contributor builds use a local debug key when the official key is unavailable. Official updates require the original private key and certificate; never publish signing material. Do not claim a contributor APK is an official update.
- Preserve encrypted credentials, Room schema4 and migrations, account scopes and cloud encryption/restore/backup behavior. Cloud configuration is optional at build time; never recreate the owner's existing hosted deployment or replace accounts as part of an app update.
- Jellyfin playback tracking is authoritative and read-only. Never restore manual watched/unwatched writes or queued playback replay.
- Find Sources remains cached-only, with original private torrent metadata, fresh confirmation checks and bounded account-scoped requests. Preserve explicit consent, mutation reservations, duplicate-add guards and no blind mutation retries.
- Auto-add is optional and series-only. Browsing, watchlisting or backup restore must not enable automation. Series Center reuses existing rule management; see `docs/AUTOADD-UX.md` and current release notes.
- Unified server changes must preserve one process/container, direct internal approval handoff, legacy protocol compatibility, metadata-only identity, stable movie/show/season folders, media read-only behavior and private migration/backup/rollback state. Read its README and VERIFICATION before editing.
- Keep named destructive confirmations. Do not use real TorBox additions/deletions as automated tests.
- Keep private keys, configured `.env`, `torznab.json`, cloud config, databases, APKs and generated archives/logs out of Git. Samples must contain placeholders only.
- Verify relevant tests and rendered UI. Record limitations explicitly; fixture success does not prove a live phone/service integration. Agent-initiated Gradle calls follow `.agents/skills/gradle-run/SKILL.md` and use the project's designated wrapper.

The portable private development folder may also contain owner-only signing/configuration and distributions. Those files are deliberately absent from this GitHub checkout. Public instructions must never imply they are bundled here.

## Android 1.5.0
Read docs/RELEASE-1.5.0.md. Following is rule-only; Jellyfin library browsing stays separate. Coming Up uses explicit Auto-add rules, never watchlist consent. Preserve source cache checks and original release metadata. Activity grid counts only dated individual Jellyfin observations. No Terminal rewrite or backend changes. Next distributable code76+.

## Android 1.5.1
Read docs/RELEASE-1.5.1.md. Tracking Activity is a month-scoped dated viewing diary; Continue Watching stays on Home. Preserve legacy preference keys, full history and passive tracking. Curated Discover requests/cache include provider/type/region; never present global shelves under a selected provider or claim public lists are service-filtered. Next release code77+.
