# STAK

Native Android media companion built with **Kotlin and Jetpack Compose**.

**Current source:** Android **1.5.0 / versionCode 75** · optional STAK Server **1.2.3**.

[Official APK releases](https://github.com/ezatvnekvan-stack/STAK/releases/latest) · [Source verification](docs/SOURCE-VERIFICATION.md)

Browse films and series, find cached sources, manage your library and follow Jellyfin playback. Library > **Following** is dedicated to Auto-add rules. Find Sources has compact Nothing-style format marks; Coming Up follows Auto-add series; Tracking includes a twelve-week activity grid. See [1.5.0 details](docs/RELEASE-1.5.0.md).

## Start developing

1. Clone this repository and open it in Android Studio or OpenCode.
2. Install **JDK 17**, Android SDK **Platform 35** and **Build Tools 35.0.0**.
3. Set `ANDROID_HOME`, or let Android Studio create an ignored `local.properties`.
4. Build the contributor variant:

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assemblePreview
```

Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assemblePreview
```

The APK is written to `app/build/outputs/apk/preview/app-preview.apk`. Without the private official keystore, Gradle uses your local Android debug key. **A contributor build cannot update an official STAK installation signed with a different key.** Do not uninstall an existing installation merely to test a contributor APK if you need its local data.

Optional cloud configuration is supplied through `STAK_CLOUD_URL` and `STAK_CLOUD_PUBLIC_KEY`; see [the configuration example](cloud-config.env.example). No admin/service key belongs in an Android build. Without configuration, the app builds with cloud sign-in unavailable; local features remain usable. The hosted service setup is documented under [deployment/stak-cloud](deployment/stak-cloud/README.md).

Official maintainers use `build-update.ps1` or `build-update.sh` with the original private keystore and deployment configuration. These are kept outside Git. Existing users must receive an APK with the same application ID and signing certificate and a higher version code; the next release must use **71 or greater**.

## Repository map

| Path | Purpose |
| --- | --- |
| `app/src/main` | Android application, UI, repositories and resources |
| `app/src/test` | Offline JVM tests |
| `app/src/androidTest` | Device/emulator UI and protocol fixtures |
| `deployment/stak-server` | Current unified optional Docker server and offline tests |
| `deployment/stak-cloud` | Optional account/backup schema and deployment guide |
| `.agents/skills` | Project-local Compose, Kotlin, design and animation skills |
| `docs` | Architecture, current feature notes and historical verification |
| `.github/workflows` | Contributor Android build and server test checks |

The obsolete web/Capacitor frontend, separate retired watcher/filter copies, built APKs, archives, caches and owner-specific runtime files are not part of this source tree. Migration support for existing watcher/filter containers remains in the unified server.

## Checks

```bash
./gradlew :app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview
./gradlew :app:connectedDebugAndroidTest  # running Android emulator/device required
python -m pip install -r deployment/stak-server/requirements-dev.txt
cd deployment/stak-server
python -m pytest -q
```

Run the server tests on Linux or in a Linux Docker container: its permission, symlink and local-HTTP fixtures target the deployed Linux environment. The server test suite uses offline fixtures. Never use a real TorBox account for automated acquisition/deletion smoke tests. Some hosted/device integration tests require explicit opt-in and are not implied by a successful local build.

See [CONTRIBUTING](CONTRIBUTING.md), [AGENTS](AGENTS.md), [architecture](docs/ARCHITECTURE.md), [documentation index](docs/README.md), [changelog](CHANGELOG.md) and [security](SECURITY.md).
