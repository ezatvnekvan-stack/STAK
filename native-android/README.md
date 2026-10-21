# STAK Native Android 0.3.0 Beta

This is a separate Kotlin / Jetpack Compose Android application. It contains no Capacitor runtime, WebView, JavaScript bundle, or companion web server. The existing React/Capacitor app is unchanged.

The native port retains STAK's monochrome surfaces, Doto headings, Hanken Grotesk text, Space Mono labels, poster grids, floating navigation, and Home / Discover / Watchlist / Tracking / Library / Settings structure. `0.3.0-native-beta.1` (version code 3) is a beta stabilization build, **not a published release and not full feature parity with STAK 1.0**. Exact pixel equality is not a release gate.

## What is in 0.3 Beta

- **Account isolation.** Watchlists, tracking, watched episodes and indexer configuration are scoped to the active account. Changing or clearing the TorBox account no longer leaks one account's private data into another. Provider credentials remain encrypted with Android Keystore.
- **Encrypted portable backups.** Backup export/import includes account data and optional connection credentials, is password-encrypted, validates the password before replacing local data, and reports partial or incompatible archives.
- **Home customization.** Shelf ordering, visibility, genre rows and navigation ordering are persisted. Home supports the hero, curated shelves, personalization from watchlist genres, watchlist, upcoming releases and Jellyfin rails.
- **Jellyfin sync.** User login/selection, library shelves and paginated watch-state synchronization. A failed server mutation is not shown as a successful local change.
- **Source search and filters.** Torznab/Stremio aggregation, hash-cache checks, deduplication, cached-TorBox filtering, and quality/title/cache filters. Matching release counts and inferred filename tags are exposed in the source sheet before adding a torrent.
- **TorBox account handling.** A replacement key is saved only after verification; rejected keys leave the working account intact. Home and Settings show verified, pending, saved-but-unverified and error states. Sign-out clears the active key and account-scoped state.
- **Native interaction work.** Reduced-motion support, spring press feedback, shared poster transitions, animated theme controls, three-column shelves/grids, backdrop-led title pages, episode rails and the original floating dock.

## Current verification

Local run on 2026-10-21:

- `assembleDebug` passed.
- `testDebugUnitTest` passed: **38 tests**, 0 failures, 0 skipped.
- `lintDebug` passed.
- `connectedDebugAndroidTest` passed on an API 35 x86_64 emulator configured to the CI viewport (`wm density 160`, `wm size 412x928`): **21/21 tests**, 0 failures, 0 skipped.
- The hosted [Native Android workflow run 39575016639](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39575016639) also passed build, unit tests, lint, emulator checks and capture comparison.
- Both previously failing checks pass: `SourceFlowTest.searchTitleFiltersCacheAndAddsOneTorrentThenOpensTorBox` and `TorBoxAccountTest.homeRejectsAnUnverifiedSavedKeyInsteadOfAdvertisingAConnection`.

The instrumented tests inject test-only HTTP fixtures into the native repository. They verify UI behavior, persistence, account isolation, source filtering and failure states, but they **do not verify live TorBox, Jellyfin, TMDb, indexer or network behavior**. No physical-device run has been recorded for 0.3 Beta. The debug APK is suitable for local evaluation.

See [`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) for completed work, known issues and recommended next tasks.

## Native design and feature work

The current direction is a polished Nothing-inspired native experience that keeps STAK's screens and features. Original React components, fonts, assets, design tokens and interactions remain the reference; thoughtful UI and animation improvements are authorized. **Full functional coverage is still unfinished, and pixel-perfect parity has not been claimed.**

The original frontend and stable app remain unchanged. Historical alpha release notes describe the published alpha, not this beta branch.

See [`parity/README.md`](parity/README.md) for original reference screenshots, comparison instructions, evidence limits and the remaining feature inventory. See [MOTION.md](MOTION.md) for interaction timing, reduced-motion behavior and evidence limits.

## Known limitations

- **Without a TMDb key**, Cinemeta supplies a plain popular catalog. Curated shelves that require TMDb filters can show a "Configure a TMDb key" error, and multiple discovery shelves can fall back to the same popular list. This is a pre-existing fallback limitation, not an account or navigation defect. A TMDb key enables the intended distinct categories.
- Home hero and shelf entries can repeat the same title in fixture tests because the fixture returns one movie for every movie catalog request. The search result row now carries a unique `home-search-result-<mediaKey>` test tag so tests target the result rather than matching text globally.
- Live provider behavior, private indexers, playback on real devices, accessibility font scales and animation frame timing remain unverified.
- Several original STAK surfaces are incomplete: all Discover filters/provider tiles, exact theme-preview/Rive timing, full TorBox file/share/storage actions, full Jellyfin collection hierarchy, and every loading/empty/failure/keyboard/Back state.
- The 0.3 APK is debug signed for local evaluation. It is not a Play Store or production release.

## Build

Requires JDK 17+, Android SDK 35 and build tools 35.0.0, with accepted SDK licenses.

```sh
cd native-android
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest # requires a running Android device/emulator
```

For CI-compatible screenshot evidence, configure the emulator with `adb shell wm density 160` and `adb shell wm size 412x928` before the first pass, then `360x928` for the second viewport pass. The repository workflow runs build, unit tests, lint and API 35 emulator checks, retaining reports and screenshots. Read the actual run result before claiming emulator success.

## Install and signing

Package: `app.stak.nativeapp`; minimum Android 8.0 (API 26). Display name: **STAK Native**. This deliberately installs beside `app.dmmstudio.cinema`, preserving the existing app's data. Transfer lists through Settings → Backups; re-enter provider credentials.

The 0.3 Beta APK is debug signed. The public 0.2 alpha used an ephemeral CI debug certificate; a new certificate cannot update that installation in place. The original application's private signing key is unavailable, so this is not an in-place replacement. See [SIGNING.md](SIGNING.md) before publishing any permanent build. No signing configuration was changed for this beta.

## Licenses

Bundled fonts are copied from the existing app under the SIL Open Font License; see `licenses/`. AndroidX, Kotlin and OkHttp use Apache 2.0; Coil uses Apache 2.0. See the repository's third-party notices for existing STAK resources. This project is independent of Nothing and is not an official Nothing product.
