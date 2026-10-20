# Build STAK 1.0.0 for Android

Source: `stak-discover-202`, descended from `v2.0.2-polish` at `d4a2991892d3272e9f9868632b938eae1d9e7e43`. Application root: `frontend/`. Current identity: `app.dmmstudio.cinema`, versionName `1.0.0`, versionCode `37`. No STAK 3.x/4.x application source is used.

## Required tools

Use Node.js 22, npm, Java 21, Android SDK platform 35 and build-tools 35.0.0. Accept the Android SDK licenses through Android Studio or `sdkmanager --licenses`. Use the checked-in Gradle wrapper rather than installing a separate global Gradle. Set `ANDROID_HOME`/`ANDROID_SDK_ROOT` through your local toolchain as appropriate; do not commit `local.properties`.

## Local checks and build

From `frontend/`, install dependencies using the lockfile included in a verified complete-source export when available:

```sh
npm ci --legacy-peer-deps --no-audit --no-fund --fetch-timeout=20000 --fetch-retries=1
```

The Git source currently has no committed lockfile. If starting from that checkout, use `npm install` with the same flags. CI's complete-source export includes its generated `package-lock.json`; retaining that file preserves the resolved dependency set of the verified build. An installation that is blocked by networking should be stopped rather than repeatedly retried. Use CI instead.

```sh
npm run lint
npm run test:ci
npm run test:adapters
npm run check:source
npm run build:android
npm run cap:sync
npm run android:prepare
npm run android:verify
```

Then build the existing debug-signed release variant:

```sh
cd android
./gradlew assembleRelease
python3 ../../scripts/verify_apk.py app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
```

Expected APK: `frontend/android/app/build/outputs/apk/release/app-release.apk`. The web build is standalone and uses the existing direct provider adapters and native plugins; it does not require a companion web server. The production version is derived from Gradle and must match `frontend/package.json`.

## GitHub Actions

The existing draft PR #26 targets `stak-compact-source-v2`. Synchronizing that PR validates its exact head commit. Manual workflow dispatch accepts an existing STAK 1.0 source ref and **builds/validates only**.

The workflow enforces lint, React and adapter regressions, source/native checks, production web compilation, production Chromium scenarios at 360px/412px dark/light, Capacitor synchronization, Gradle compilation, packaged asset/plugin verification, APK package/version/signature inspection, and complete-source ZIP integrity/extraction. Dependency installation, browser and APK steps have bounded job timeouts. The workflow verifies and reuses the hosted runner's preinstalled SDK; SDK-manager version checks have a 15-second timeout and only missing platform/build-tools components are installed with a 120-second limit. It does not replace a working SDK or refresh unrelated SDK packages.

Artifacts:

- `STAK-1.0.0-APK`: compiled APK, SHA-256 checksum, package/version identity, public signing certificate and signing-compatibility report.
- `STAK-1.0.0-visual-evidence`: fixture-based screenshots and browser results.
- `DMM-Studio-Android-FINAL`: complete source, generated dependency lockfile, actual CI results, source manifest and compiled APK when the build succeeded.

The authorized one-time publisher runs only after successful validation on the same-repository `stak-discover-202` PR. It publishes the distinct tag `stak-v1.0.0`, title **STAK 1.0.0**, without moving existing tags or overwriting assets. Once that release exists, later development pushes validate without republishing it. The older `v1.0.0` release remains preserved. Downloaded release APK/source assets are byte-compared and checksum-verified by the publishing job.

## Signing and update compatibility

The release build continues to use the existing **debug signing configuration**. Fresh CI runners generate temporary debug keystores, so certificates can differ between builds. Public certificate verification proves the APK's signature is valid; it does not prove upgrade compatibility with an installed copy.

A data-preserving update requires the same package identity, a suitable versionCode, and the installed application's matching signing key. Code 37 is above the verified original 2.0.2 code 31 and previous development code 36; other installed versions must be checked separately. Resetting the visible name to 1.0.0 does not reset Android's versionCode ordering.

The matching original private signing key is unavailable in this environment. Keep signing keys outside the repository and source archives. **Do not uninstall STAK or clear storage to bypass a signature mismatch.** Preserve the existing installation and export a credential-free backup; use the matching original signing key for an in-place update.

## Verification limits

Read the actual workflow run, `CI_TEST_RESULTS.md`, APK identity and signing report before claiming success. Screenshots use intercepted test-only providers. No physical-device installation, live private-server success, TalkBack/keyboard compatibility, or measured FPS/startup/memory performance is established by these automated checks. Failed builds may still export source; an exported ZIP alone is not proof that an APK was generated.
