# STAK Native Android 0.1.0 alpha

This is a separate Kotlin / Jetpack Compose Android application. It contains no Capacitor runtime, WebView, JavaScript bundle, or companion web server. The existing React/Capacitor app is unchanged.

The native port retains STAK's monochrome surfaces, Doto headings, Hanken Grotesk text, Space Mono labels, poster grids, floating navigation, and Home / Discover / Watchlist / Tracking / Library / Settings structure. This is an initial native alpha, **not full feature parity with STAK 1.0**.

## Original-app parity work

The user requires the original React/Capacitor UI and behavior, screen for screen, in Compose. The original components, CSS, fonts, assets and motion policy are the specification. Design and animation improvements are deferred. **Pixel-perfect visual and functional parity has not been achieved or verified. Do not publish this branch as a replica.**

This branch replaces the simplified alpha layouts with the original dock, Settings destination index, theme controls, three-column shelves/grids, backdrop-led title screen and episode rail. It also adds direct TMDb browsing/details, Jellyfin user login and selection, source search, TorBox actions, remote and person destinations. These implementations need the full parity review; their presence does not establish equivalence.

See [`parity/README.md`](parity/README.md) for original reference screenshots, comparison instructions, evidence limits and the blocking gap inventory. Hosted API 35 tests retain matching-viewport captures and functional smoke reports. Fixture tests cannot verify private live services.

The original frontend and stable app remain unchanged. Existing alpha release notes describe the published alpha, not the unfinished branch.

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

## Licenses

Bundled fonts are copied from the existing app under the SIL Open Font License; see `licenses/`. AndroidX, Kotlin and OkHttp use Apache 2.0; Coil uses Apache 2.0. See the repository's third-party notices for existing STAK resources. This project is independent of Nothing and is not an official Nothing product.
