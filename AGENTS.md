# STAK development

Current Android source: **1.5.9 / versionCode 85**. Next distributable versionCode: **86+**. Current optional backend: **STAK Server 1.2.3**.

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

## Android 1.5.2 — current override
Owner explicitly restored original 1.4.0 Tracking and Discover. Read docs/RELEASE-1.5.2.md. Do not reintroduce the 1.5 diary, heatmap hub or Discover redesign. Following, source badges and Auto-add Coming Up remain. Original signing/cloud configuration preserved; next distributable code78+.

## Android 1.5.3 — optional Glyph background
Read docs/RELEASE-1.5.3.md. Current remains the default background. Glyph is an opt-in vector background with one-shot route/confirmed-add signals; preserve reduced motion, modal/scroll/lifecycle pausing and persisted backup-compatible preference. IMDb is compact and white; Dolby marks share other format badge styling. No Series Center shortcut on Details; Auto-add stays. The 1.5.2 original-page override still applies. Next distributable code79+.

## Android 1.5.4 — approved cinematic refinements
Read docs/RELEASE-1.5.4.md. The owner approved shared outline Movies/Series selectors, edge-to-edge centered Home hero, larger centered Details with animated seasonal entries, and shared filled classification stamps. Tracking/Discover retain 1.4 content, flows and data behavior; only their type-selector and age-rating presentation call sites adopt shared visual controls. Page tabs retain underlines. Current/Glyph settings and all acquisition/cloud/passive-tracking guards remain. No BetterPosters integration or backend changes. Next distributable code80+.

## Android 1.5.5 — approved cinematic follow-up
Read docs/RELEASE-1.5.5.md. Larger rectangular hero fades to the actual background with clipped pager layers. CertificationBadge is transparent/off-white outline everywhere, unchanged compact size. Details genres precede a compact wrapping facts/IMDb line; Sources uses the same outline treatment as secondary actions. Quick source filters remain visible and adjacent; no duplicate card quality heading. Movie library status omits video-height/HDR subtitle, retaining Media Quality. Home/Details independently prefer neutral-language textless artwork and original-resolution images/logos, top-aligned with no steady zoom. Keep ordinary grid poster URLs and metadata provider preference; null language is only a textless hint and absent art retains a fallback. No BetterPosters, backend or watched-state changes. Next code81+.

## Android 1.5.6 — approved six-step refinement
Read docs/RELEASE-1.5.6.md. Home uses a taller portrait hero without app header controls or synopsis. Continue Watching prefers original-resolution English branded backdrops, a half-next-card rail, a cached local blurred information band and a short progress bar; Home/Details remain textless. Details artwork is 20% taller and extends under metadata, with action sizes preserved. Sources uses single-frame large quick filters, upper-right size, no Recommended text row, and a plus/check action only after acknowledgement. Jellyfin sort replaces the main library chooser; the chooser remains in filters. Watchlist totals are a compact text line. Preserve public-artwork identity checks, lazy/bounded work, reduced motion, current/Glyph, restored Tracking/Discover, passive tracking and all acquisition/cloud/account guards. Next code82+.

## Android 1.5.7 — compact Home and Continue Watching
Read docs/RELEASE-1.5.7.md. Home hero is approximately half the screen, retaining the textless/original artwork, smooth fade, logo/facts and reduced-motion behavior. Continue Watching uses original English branded show/movie backdrops; episode stills are never its Jellyfin fallback. Resolve a missing show identity from its explicit account-scoped parent only. Episode/time info sits below the artwork without a separate title; no lower blur. Short progress stays on the image and half-next-card geometry/long-press remain. All 1.5.5/1.5.6 source, Details, Library, Watchlist and acquisition/passive/cloud guards remain. Next code83+.

## Android 1.5.8 — Continue Watching Nothing panel
Read docs/RELEASE-1.5.8.md. Continue Watching alone uses a single outlined panel enclosing 16:9 art and a matte footer with decorative play mark and episode/time text. No blur or repeated title. Short red segmented progress, half-next-card geometry, parent-series English art, long-press and original Details navigation remain. Home hero stays half-screen. Next code84+.

## Android 1.5.8 replacement / code84
The owner superseded the Nothing panel with original 1.4 Continue Watching geometry, status badge and overlaid information. Use neutral/textless show or movie backdrop with a separate logo, never an episode still or English branded backdrop. Short progress remains. Same v1.5.8 release is replaced with code84 for in-place updates; next code85+. See current release notes.

## Android 1.5.9 — current Home override

Read docs/RELEASE-1.5.9.md. Home hero uses TMDb's mixed daily trending order, TRENDING TODAY without a visible counter, a longer transparent artwork fade and a smaller gap to Continue Watching. Continue Watching now uses the episode's own still for series (explicit parent backdrop only on image error), a compact clean typographic wordmark instead of unverified studio-branded logos, and bottom inline NEXT UP/IN PROGRESS plus episode/time. Short segmented progress remains; the owner's final correction removes its numeric percentage. Coming Up uses a wide date-led card, full width for one item and a next-card peek for multiple items; no air time is invented. Only explicit Auto-add rules supply Coming Up. Tracking/Discover, Details, data/backup/account guards and cached-only read-only behavior remain. No server update. Next code86+.
