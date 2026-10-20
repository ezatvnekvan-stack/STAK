# Nothing details and Jellyfin polish — Android 14.1.0 (build 15)

This pass preserves the existing Nothing identity, screens, providers and local data. It removes embedded playback rather than introducing another engine. The separate Linux application and main branch are untouched.

## Implemented

- Removed the only embedded player found in the source: the YouTube trailer iframe. Trailer controls now open a validated YouTube watch link through the existing external-browser bridge. The dialog retains selection, invalid-data recovery and launch-error retry. No video, audio or iframe player is mounted; CSP blocks all embedded frames. Jellyfin remote playback and TorBox external file links remain.
- Replaced the vertical episode stack on metadata title details and the vertically scrolling Jellyfin episode list with one-row horizontal, snapping cards. Cards retain images, synopsis, real ratings, watched/resume markers and exact season/episode actions. The selected card can reveal itself horizontally without scrolling the page. Arrow/Home/End keyboard navigation and native button focus remain available.
- Improved Jellyfin series selection: sorted real episode numbers, choose an in-progress/unwatched episode, keep season/unwatched filters consistent with the actual playback and watched target, and show an honest filtered-empty state. Do not send the Series container as an episode.
- Reused account-scoped queries for Jellyfin item/device requests. Obsolete title responses cannot replace another route. Server-change/unmount guards prevent late mutation UI updates, and playback/mark controls are disabled while a refresh fails or is incomplete. Concurrent duplicate mutations are blocked.
- Watched-state UI updates only after the server accepts the write. A subsequent tracking-sync failure is reported separately; a failed write does not show success. Device loading failures are distinct from an empty client list.
- Playback retains the chosen client in the Remote URL. A missing client no longer silently switches controls to a different television. Remote controls are disabled on device-query errors.
- Authenticated landscape episode artwork now tries the episode's Primary still before series fallback; posters retain series-first behavior. Existing authenticated image caching/concurrency is preserved. Episode requests include Overview for the selected synopsis; malformed episode lists reject instead of crashing rendering.
- Home Jellyfin connection errors have retry/settings controls. Playback-progress bars derive from actual saved/runtime ticks when the provider omits a percentage.
- Added restrained card press/border feedback, touch-friendly single-row spacing, theme-aware focus rings and trailer-button surfaces. Android/browser reduced motion disables animations/transitions and horizontal programmatic smooth scrolling. No new visual preset or animation library.
- Incremented Android version to 14.1.0 / code 15, preserving app.dmmstudio.cinema and API 24 minimum.

## Verification and delivery

Local dependency-free tests: 49 passed. Structural source check: 170 modules passed. Android configuration and whitespace checks passed. Local npm installation was attempted but failed with proxy EPERM; this is not a successful local React build. React/Jest, ESLint, production build, Capacitor synchronization, Gradle APK compilation, APK assets/plugin/CRC inspection, apksigner verification and ZIP extraction are run by the existing GitHub Actions workflow. Read its actual results and the exported CI_TEST_RESULTS.md; creating tests/workflows is not evidence they passed.

The first equipped CI regression run confirmed 17 suites / 68 React tests passed, alongside 49 adapter cases and 170 structural modules. Its hook-cleanup production lint warning was corrected; final compilation/APK outcomes must be read from the final CI run.

New regressions cover horizontal keyboard episode selection and real TMDb badges; external-only trailers/invalid lists/browser failures; Jellyfin resume selection, filtered action targets, device failures and failed watched writes; and retaining the chosen remote client without falling back to another client.

The APK inspector also checks that CSP blocks frames and the bundled entry scripts do not contain the removed YouTube embed endpoints. This does not substitute for a device test.

## Remaining validation / limitations

No live provider, physical-device, emulator, visual screenshot, scroll-frame, memory or battery test has been performed. Confirm horizontal swipes, long seasons, focus, light/dark themes, status/navigation bars, Android/browser reduced motion, encrypted-credential upgrades, external browser behavior, actual artwork and real Jellyfin client support on a phone. The existing Browser plugin may open a browser Custom Tab; this is external browser playback, not a DMM media engine. A YouTube app handoff depends on Android/browser link handling.

The Jellyfin episode query retains its existing 1,000-episode bound. Full history conflict reconciliation, hosted sharing, automatic filesystem transfer and other previously documented infrastructure-dependent features were not added. The broad phrase “better in every way” is addressed here through concrete existing workflows; this is not a certification that every integration works live.

CI debug certificates are not guaranteed to match an installed build. An in-place data-preserving upgrade requires the original signing certificate. Do not uninstall or clear app storage to bypass a mismatch. Source exports exclude credentials, .env, dependencies, caches, signing keys and generated build directories; the CI artifact includes the debug APK only after a real successful build. BUILD_ANDROID.md contains rebuild instructions.
