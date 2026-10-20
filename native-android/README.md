# STAK Native Android 0.1.0 alpha

This is a separate Kotlin / Jetpack Compose Android application. It contains no Capacitor runtime, WebView, JavaScript bundle, or companion web server. The existing React/Capacitor app is unchanged.

The native port retains STAK's monochrome surfaces, Doto headings, Hanken Grotesk text, Space Mono labels, poster grids, floating navigation, and Home / Discover / Watchlist / Tracking / Library / Settings structure. This is an initial native alpha, **not full feature parity with STAK 1.0**.

## Implemented

- Cinemeta movie/series browsing, debounced search, pagination, metadata and episode lists.
- Persistent Room watchlist, watched-title journal, individual episode marks, and a month-based calendar following watchlisted series.
- Jellyfin library (up to 300 titles), resume cards, watched/unwatched mutation, authenticated artwork, and external Jellyfin links.
- TorBox task progress, file lists, and external playback/download links.
- Existing `dmm-backup` v1 JSON import (watchlist, tracking, episode marks), native export/import, merge without overwriting existing entries. Credentials and legacy shares/Home configuration are not imported. Encrypted legacy backups must first be exported as ordinary JSON from the existing app.
- AES-GCM credentials protected by Android Keystore; system certificate verification; no bundled API keys; no credentials in the local database or exported backup.
- Resizable Android Watchlist widget.

## Motion

- Shared poster bounds animate into and out of the title screen (360 ms); each source has a distinct identity.
- Navigation and segmented indicators use a restrained, retargetable spring.
- Press feedback uses 0.975 / 0.98 scaling and optional Android haptics; success feedback follows successful mutations.
- Draggable Material sheets follow touch and support Android Back. Reduced motion uses a static bottom dialog.
- Watched checks draw on and reverse; segmented progress interpolates real values.
- Pull-to-refresh dot sequences follow actual requests; indeterminate status never fabricates a percentage.
- Dot animation stops when the app is not resumed. App and live system reduced-motion settings are respected. System animation duration scaling also applies to Compose animation clocks.
- Navigation Compose handles Android predictive Back; scroll state is retained through navigation. Physical-device gesture quality and frame timing still need measurement.

## Deliberate alpha limits

Advanced TMDb/provider filters, cast/person pages, source aggregation/indexers, TorBox creation/deletion, account switching/isolation, Jellyfin remote controls, full playback tracking, theme accents/AMOLED/compact preferences, and automatic background sync are not ported yet. Episode marks are local; marking a series title watched does not mark every episode. Jellyfin playback opens its external web/client interface. There is no internal media player. The calendar depends on provider episode dates. Continue/Tonight widgets are future work; the included widget shows Watchlist.

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
