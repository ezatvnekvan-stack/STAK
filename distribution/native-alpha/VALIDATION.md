# STAK Native alpha validation

Source commit: `514de94bfa07df861a1c0268180890f7cd56ea89`

GitHub Actions: https://github.com/ezatvnekvan-stack/STAK/actions/runs/39473230190

- CI build, unit tests, Android lint and Android 15 / API 35 emulator job: **PASS**.
- 10 backup/data/metadata regression tests; 2 Android interaction tests passed.
- Emulator tests cover native navigation, reduced motion, saved-title opening, watched/Undo, episode marking, normal/static sheets, and Back navigation.
- The distributed APK is copied unchanged from this successful CI run's artifact.
- APK signature verified using Android build-tools 35.0.0. Public certificate and package identity are included.
- Package `app.stak.nativeapp`; version `0.1.0-native-alpha`; versionCode `1`; minimum API 26; target API 35.
- Packaged ZIP/DEX inspection found no Capacitor classes, HTML UI, or JavaScript bundle.
- Local standalone Gradle 8.11.1 build, unit tests and lint also passed.
- Live public Cinemeta catalog/search/metadata requests responded successfully during development. Private Jellyfin/TorBox credentials were not available for end-to-end verification.
- 5 emulator screenshots retained. Fixture screenshots demonstrate interactions, not live library data.

This is a debug-signed native alpha that installs alongside the original STAK. It is not a full feature-parity or production release. No physical-device validation, measured frame-rate result, or live private-provider success is claimed. See RELEASE_NOTES.md and the native README for migration limits.
