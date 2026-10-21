# STAK Native Android 0.2.0 alpha

This is a separate Kotlin / Jetpack Compose Android application. It contains no Capacitor runtime, WebView, JavaScript bundle, or companion web server. The existing React/Capacitor app is unchanged.

The native port retains STAK's monochrome surfaces, Doto headings, Hanken Grotesk text, Space Mono labels, poster grids, floating navigation, and Home / Discover / Watchlist / Tracking / Library / Settings structure. This is an initial native alpha, **not full feature parity with STAK 1.0**.

## Native design and feature work

The current direction is a polished Nothing-inspired native experience that keeps STAK's screens and features. Original React components, fonts, assets, design tokens and interactions remain the reference; thoughtful UI and animation improvements are authorized. Exact pixel equality is no longer a release requirement. **Full functional coverage is still unfinished, and pixel-perfect parity has not been claimed.**

This branch replaces the simplified alpha layouts with the original dock, Settings destination index, theme controls, three-column shelves/grids, backdrop-led title screen and episode rail. It also adds direct TMDb browsing/details, Jellyfin user login and selection, source search, TorBox actions, remote and person destinations. These implementations still need feature and interaction review; their presence does not establish full equivalence.

See [`parity/README.md`](parity/README.md) for original reference screenshots, comparison instructions, evidence limits and the remaining feature inventory. Hosted API 35 tests retain matching-viewport captures and functional smoke reports. Fixture tests cannot verify private live services.

The original frontend and stable app remain unchanged. Existing alpha release notes describe the published alpha, not the unfinished branch.

See [MOTION.md](MOTION.md) for the interaction timing, reduced-motion behavior and evidence limits.

## Build

Requires JDK 17+, Android SDK 35 / build tools 35.0.0, and accepted SDK licenses.

```sh
cd native-android
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest # requires a running Android device/emulator
```

The repository workflow runs build, tests, lint and API 35 emulator smoke checks, retaining reports and screenshots. Read the actual run result before claiming emulator success.

## Install and signing

Package: `app.stak.nativeapp`; minimum Android 8.0 (API 26). Display name: **STAK Native**. This deliberately installs beside `app.dmmstudio.cinema`, preserving the existing app's data. Transfer lists through Settings → Backups; re-enter provider credentials.

The alpha APK is debug signed and distributed as a GitHub prerelease. It is for evaluation, not a Play Store production release. The original application's private signing key is unavailable, so this is not an in-place replacement. Future stable updates need a persistent owner-controlled signing key. Never uninstall the original app to bypass signing errors.

The 0.2 native alpha also uses a different debug certificate from the first Native alpha, so Android cannot update that earlier alpha in place. Existing Native alpha users should retain an exported Native JSON backup before arranging a fresh Native installation. The original STAK package remains separate.

## Licenses

Bundled fonts are copied from the existing app under the SIL Open Font License; see `licenses/`. AndroidX, Kotlin and OkHttp use Apache 2.0; Coil uses Apache 2.0. See the repository's third-party notices for existing STAK resources. This project is independent of Nothing and is not an official Nothing product.
