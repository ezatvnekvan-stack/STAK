# STAK Native

Native Android media management with Kotlin and Jetpack Compose. This is the current **1.0.0** application; the old web/Capacitor implementation has been replaced on the main branch.

## What it does

- Discover movies and series, manage a watchlist and view upcoming releases.
- Browse Jellyfin libraries, collections and versions; request authorized library scans.
- Follow Jellyfin playback automatically with read-only Watching, History, Stats and monthly Calendar.
- Search configured Torrentio, AIOStreams and Torznab/Prowlarr sources, with strict TorBox cache verification in Find Sources.
- Manage TorBox torrents and browse an existing Zurg WebDAV endpoint.
- Inspect available media metadata and play trailers. Full media playback stays in Jellyfin or another external client.

There is no required STAK backend, Sonarr/Radarr integration or local media downloader. The optional library-filter helper under `deployment/` is needed only to expose explicitly STAK-approved torrents to an existing Jellyfin/Zurg installation.

## Build

Install JDK 17 or newer and Android SDK Platform 35 / Build Tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or configure `sdk.dir` in an untracked `local.properties` file.

Windows:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assemblePreview
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assemblePreview
```

APK output: `app/build/outputs/apk/preview/app-preview.apk`. The preview variant is optimized and non-debuggable. Its application ID is **`app.stak.nativeapp.parity`**, retained for existing installations. Current version code is **47**, so the new 1.0.0 version name can update the previous development builds.

## Signing and updates

The official private signing key is **not included**. Without it, a local preview build uses Android's locally generated debug key and cannot update an official installed APK. Use a separate emulator/device or uninstall that official installation first; uninstalling removes its local data. Official release APKs keep the original signing identity and support in-place updates.

For an authorized official build, keep the private keystore in the ignored `signing/stak-preview.keystore`, or set `STAK_KEYSTORE_PATH`. Custom keystores may also use `STAK_KEYSTORE_PASSWORD`, `STAK_KEY_ALIAS` and `STAK_KEY_PASSWORD`. Do not commit keys, credentials or generated APKs.

## Configure the app

The onboarding setup and Settings configure optional Jellyfin, TorBox, metadata and source accounts. Credentials remain encrypted on the device. API keys and server addresses are not shipped in this repository. LAN-only services require home Wi-Fi or an existing VPN when away; the app does not expose services publicly.

## Development

- `app/src/main`: application source, Compose UI and bundled assets.
- `app/src/test`: JVM regression tests.
- `app/src/androidTest`: emulator/device UI and protocol tests with isolated synthetic fixtures.
- `deployment/stak-library-filter`: optional Docker helper, example configuration and tests.
- `docs/ARCHITECTURE.md`: current architecture and development constraints.

Run selected Android tests with a running emulator:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.stak.nativeapp.CompactLibraryUiTest
```

The repository excludes local build output, private keys, credentials, SDK paths, old APKs, screenshots and session transcripts. Release APKs are distributed through [GitHub Releases](https://github.com/ezatvnekvan-stack/STAK/releases).

Physical-device performance and live integrations still depend on the configured servers, accounts and Android device. No fixed frame rate or always-on phone monitoring is promised.
