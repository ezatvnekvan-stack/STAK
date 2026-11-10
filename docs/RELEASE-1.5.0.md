# STAK 1.5.0 — Following, source badges and viewing activity

Android versionCode 75 (codes 71–74 belonged to withdrawn experiments). Based on the exact published 1.4.0 source. No Terminal interface, trailer rewrite or backend migration is included.

## 1. Following
Library tabs are Jellyfin / TorBox / Following. Following lists saved Auto-add rules only, with artwork, concise confirmed/pending/paused/attention state and search. Open a rule for existing settings, pause/resume, per-episode status and named stop confirmation. Library series remain browsable on Jellyfin. Existing title-to-Series Center detail links remain available without mixing library items into the Following list.

## 2. Find Sources
Compact header and selection controls. HDR, Atmos, WEB-DL and Remux stay visible. App-drawn monochrome format badges use Doto/Space Mono, restrained outlines and a red selected-filter marker. Labels are derived from known flags/release text, never proof of actual media inspection. Release cards prioritize resolution/type and size; original name and quality report remain accessible. Cached state and explicit Add/Adding/Added–Files actions retain existing fresh confirmation, account, private torrent and duplicate-add guards. Individual-episode expansion moves below season-pack results. No provider/search protocol changes.

## 3. Coming Up
Uses explicit enabled Auto-add rules, including paused followed series, excluding pending stop requests. No watchlist-only films or series. Known season selections/specials are respected, events require season/episode coordinates, sort by release date, and remain distinct per episode. Home hides this section without known upcoming episodes. Existing metadata provider/date limitations remain.

## 4. Tracking
Continue Watching, then a twelve-week Monday-aligned daily activity grid, then Recently Watched. Replaces the Month in Review and separate habit rows on the hub; detailed statistics and full History remain reachable. Dated passive Jellyfin observations only, no synthetic historical activity. Red intensity indicates count; white outline today; selection reveals exact date and titles. Prior twelve-week periods are available; future cells are disabled. Estimated runtime is media runtime, never measured watch duration. Calendar remains monthly, with Today integrated into month navigation and distinct today/selection marks.

## Compatibility
App ID app.stak.nativeapp.parity; original signing identity and encrypted settings/Room/cloud configuration retained. No Linux update. No manual watched/unwatched mutation. Do not uninstall to update. Contributors build with local debug signing; official release requires owner-private build inputs.

## Verification
Results are recorded below after final validation. Isolated emulator fixtures do not prove owner-phone rendering or live TorBox/Jellyfin integration. No owner-account media additions/deletions are used as tests.


### Verified results (2026-11-10)

Managed Gradle workflow 83c962eaa372368171a52a3a6d7c1bda:

- “Do the four approved 1.5 UI changes compile against the restored 1.4 baseline?” Initially failed on missing imports; corrected.
- “Do Following, auto-add-only Coming Up, source cache guards and daily activity interactions pass regression and device tests?” Exposed a pre-existing ActivityDay name collision, new test encoding mismatch and obsolete overview assertion; corrected. No production account/cache safeguards were weakened.
- “Do the final Following, source badge/cache safeguards, Auto-add upcoming selection and twelve-week activity/calendar UI pass all regression checks?” Final PASS: debug JVM 498/498, preview JVM 498/498, Android UI 16/16, zero failures/errors/skips. Duplicate Following header removed after the fixture identified it. Includes calendar interaction, large-font source card, no-add-without-cache, original detail navigation and Auto-add management.
- “Does the official 1.5.0 code75 APK build with existing cloud configuration and verify the original package and signer?” The wrapper rejected the PowerShell launcher before running a build. The existing Gradle launcher was then used from an isolated environment holding the original cloud inputs.
- “Does the official 1.5.0 preview assemble with the original cloud build inputs?” PASS. Original build-update.ps1 -ExportOnly then verified package/version/certificate. Both existing cloud URL and publishable client key were verified present in optimized DEX without printing them.

Inspected rendered Following, source details/badges, large-text source card, activity day selection and monthly calendar. Original-signer APK installed in place on isolated emulator and cold-launched. Physical owner phone and real owner service integrations are not asserted. No live TorBox mutation or watched-state write was tested.

APK: versionName 1.5.0, versionCode 75, app.stak.nativeapp.parity.
SHA-256: 5513063544ec1b3d1197bdb3ee78665a578b3d9939d6d89b4a1e2216b45754c1
Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430

Workflow finish was attempted; Windows retained locked temporary 0001.log (WinError32). Build/test results are unaffected; temporary-log deletion is not claimed. Next distributable versionCode: 76+.

### CI portability follow-up
Initial GitHub Linux validation passed all server tests but failed the legacy watcher dropout fixture. The test previously closed an ephemeral listener before further HTTP requests. It now keeps ownership of the port and closes accepted real TCP connections to deterministically simulate interruption; all existing pending-state, no-false-confirmation and readable-error assertions remain. Production code/APK is unchanged. Failure-only JVM XML artifacts now aid CI diagnosis. Managed question: "Does real TCP interruption preserve pending watcher rules and readable errors without relying on ephemeral port reuse?" PASS in debug and preview round-trip suites.
