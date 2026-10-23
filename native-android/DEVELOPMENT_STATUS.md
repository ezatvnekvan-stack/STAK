# STAK Native 0.3 Beta — development status

Updated: 2026-10-21  
Branch: `native-opencode-0.3`  
Version: `0.3.0-native-beta.1` (version code 3)  
Package: `app.stak.nativeapp`  
Minimum Android: 8.0 (API 26)

This file records the stabilization work completed for the 0.3 Beta. It is not a release note and does not claim full STAK 1.0 parity.

## Completed work

### Instrumented-test stabilization

- `SourceFlowTest` no longer clicks the first node matching the fixture title. Home legitimately repeats that title in the hero and discovery shelves, so search result posters now expose `home-search-result-<mediaKey>` and the test targets that unique tag.
- `TorBoxAccountTest.homeRejectsAnUnverifiedSavedKeyInsteadOfAdvertisingAConnection` now waits for the account store's verification result (`state.error`) before asserting the rendered badge. It does not increase the timeout or rely on polling UI text while verification is still in flight.
- Screenshot evidence now uses `MediaStore` through a shared `saveEvidence` test helper. Direct writes to `/sdcard/Pictures` fail on API 29+ and previously made `SourceFlowTest` fail after its functional flow had already passed.
- The full 21-test instrumentation suite passes on an API 35 x86_64 emulator at the CI viewport (`wm density 160`, `wm size 412x928`).

### TorBox account-state stabilization

Root cause: Home and Settings read the saved key imperatively and each triggered verification, while `StakViewModel` also verified whenever the account scope changed. Overlapping requests and account-scope recomposition could cancel one request and leave the UI showing a stale pending state.

Changes in `TorBoxAccount.kt`:

- Added `configured` to `TorBoxAccountState` so screens no longer read the secret key to decide whether an account exists.
- Verification is owned by the ViewModel's account-scope flow; screen-level `LaunchedEffect` refresh calls were removed from Home and Settings.
- `refresh()` is serialized by a mutex, skips duplicate work for a key with a definitive result, and clears `loading` if cancellation wins.
- A revision guard prevents an old verification result from overwriting a newer account decision.
- `connect()` now participates in the same lock and checks the revision before saving, so signing out during verification wins over the older connect.
- A rejected replacement key still leaves the working key and account data intact.

`Screens.kt` and `SettingsScreen.kt` now derive account status from `configured`, `loading`, `data` and `error` instead of independently reading the credential.

## Test results

Run locally on 2026-10-21 against the uncommitted 0.3 Beta changes:

| Check | Result |
| --- | --- |
| `assembleDebug` | Passed |
| `testDebugUnitTest` | Passed — 38 tests, 0 failures, 0 skipped |
| `lintDebug` | Passed |
| `assembleDebugAndroidTest` | Passed |
| `connectedDebugAndroidTest` (API 35 emulator) | Passed — 21 tests, 0 failures, 0 skipped |
| Hosted [Native Android workflow run 39575016639](https://github.com/ezatvnekvan-stack/STAK/actions/runs/39575016639) | Passed — build, unit tests, lint, emulator suite and capture comparison |

The hosted run completed in 9m53s on commit `a0079f8`. The two target tests both pass:

- `SourceFlowTest.searchTitleFiltersCacheAndAddsOneTorrentThenOpensTorBox`
- `TorBoxAccountTest.homeRejectsAnUnverifiedSavedKeyInsteadOfAdvertisingAConnection`

APK for local evaluation: `native-android/app/build/outputs/apk/debug/app-debug.apk` (debug signed).

Both local and hosted emulator results use injected fixtures and do not verify live services. The hosted run used the CI emulator profile and viewport; the local run used an API 35 x86_64 AVD with the same density and size overrides.

## Duplicate Home titles: finding

Repeated fixture titles are expected in the test environment, not a duplicate-row UI defect:

- The fixture returns the same movie for every `/catalog/movie/` request.
- Home's hero uses the trending movie row.
- With no TMDb key, discovery shelves fall back to the same Cinemeta popular catalog, so the hero, "Trending movies" and "Top rated movies" can all contain the fixture title, plus the search result.

The unique search-result tag removes the selector ambiguity. This does not change Home behavior.

Known fallback limitation, not fixed in this pass: without a TMDb key, filter-based shelves such as "Released this year" can show a "Configure a TMDb key to use curated discovery and filters." error, and unfiltered discovery shelves can repeat the same popular list. With a TMDb key, the shelves use distinct categories. A future pass should hide unavailable curated shelves or show one clear "Add a TMDb key" prompt instead of per-shelf errors.

## Unresolved issues and risks

- Live TorBox, Jellyfin, TMDb and indexer behavior is not verified by fixture tests.
- No physical-device run has been recorded for 0.3 Beta. The user will test the debug APK on an Android phone.
- Hosted CI must be run after pushing this branch; only local checks are recorded here.
- The 0.3 APK remains debug signed. The public 0.2 alpha used an ephemeral debug certificate, so 0.2 cannot be updated in place by a differently signed build.
- The no-TMDb Home fallback remains visually noisy (possible duplicate discovery rows and key-required errors).
- Full feature parity is still incomplete: Discover filters/provider tiles, exact theme-preview/Rive timing, the full TorBox file/share/storage surface, the full Jellyfin collection hierarchy, and broad loading/empty/failure/keyboard/Back-state coverage.
- Accessibility font scales and animation frame timing on real hardware are unverified.

## Recommended next tasks

1. Push `native-opencode-0.3` and confirm build, unit, lint and the full emulator suite in GitHub Actions.
2. Test the debug APK on a physical Android 8+ phone, focusing on TorBox sign-in/replacement/sign-out, account switching, source filters, playback and Jellyfin sync.
3. Improve the no-TMDb Home experience: show one setup prompt or suppress shelves that cannot be honored, instead of duplicate fallback rows and per-shelf errors.
4. Add instrumentation coverage for cancellation and account switching during in-flight verification, beyond the current start-up error case.
5. Complete the remaining TorBox file/share/storage actions and Jellyfin collection workflows, then expand failure/empty-state coverage.
6. Only after the owner generates and stores a permanent signing key, produce a signed candidate through the documented `android-release` environment. Do not change signing configuration as part of a beta stabilization commit.

## Discover and connection fixes (2026-10-23)

The continuation from `native-search-parity-build` adds persisted Discover preferences and retained filter drafts, type-specific genres/US age ratings, decade ranges, searchable ISO language choices, removable filter chips, explicit year validation and retries that preserve the selected filters. Discover searches both movies and series, enforces loaded-page filters for search/Trending, checks regional provider availability and TV content ratings, maps series sort fields correctly and retains next-page navigation after an empty filtered page. Saved Discover preferences are included in portable backups.

The source sheet uses a regular poster inside its separate modal window. A navigation shared element cannot be registered across that window boundary with normal motion; the prior reduced-motion source tests did not exercise this path. A new instrumented regression opens, closes and reopens Find sources with normal motion enabled.

Jellyfin username/password login now sends standard `Authorization: MediaBrowser …` client identity before receiving a token, matching the Capacitor login flow. Authenticated calls send consistent modern and legacy headers. Pasted `/web/index.html` links normalize to the server root while preserving a reverse-proxy base path, and the saved session uses the tested address. Passwords are sent unchanged in the JSON body and never included in error messages. HTTP 400 errors provide protocol/port/base-path guidance. Tests cover password and API-key requests, rejected login and URL normalization; live-server success still requires verification against the user's server.

Remaining Discover work includes suggestions, verified IMDb filtering, hide-watched/library controls and further provider-service grouping. Other remaining TorBox/Jellyfin workflows, full visual/motion/accessibility parity and live-provider/physical-device validation remain as listed above. Use the linked PR workflow reports for the actual validation results of this continuation.
