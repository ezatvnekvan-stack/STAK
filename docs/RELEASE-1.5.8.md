# Android 1.5.8 replacement / code84 — Original Continue Watching

This owner-authorized replacement supersedes the Nothing panel in the existing v1.5.8 release. Version name and release stay 1.5.8; versionCode increases from83 to84 so installed apps can update in place.

Continue Watching restores the original 1.4 width, 148dp height, outlined rounded card, dark readability gradient, status badge and overlaid information/percentage. The progress bar stays short. A separately loaded logo replaces the old text-title area; missing logos retain a readable title fallback. Large text gets a bounded height allowance. The matte footer is removed.

Artwork now prefers the highest-resolution neutral/textless backdrop plus a separate English logo. English branded backdrops are not selected. Episodes continue to use their explicitly identified parent show's backdrop and public metadata, never an episode still. Original playback identity, account guards, lazy bounded artwork loading, long-press, press feedback and reduced motion are preserved. Home hero and other screens are unchanged.

## Verification

Managed `gradle_run.py` workflow `5049cf2facbf205721ada687c7b4a94a`, designated `gradlew-local.cmd`, `--no-daemon`.

"Helyes a visszaállított Continue Watching és elkészül a frissítés?" — PASS: 8 focused ContinueArtwork JVM tests (`:app:testDebugUnitTest`), 11 emulator cases (`:app:connectedDebugAndroidTest`, Cinematic156UiTest and PremiumHomeUiTest) and optimized `:app:assemblePreview`. Neutral-backdrop selection, separate branding, explicit parent identity, restored geometry and short progress were checked; a rendered fixture was inspected.

Original signer and existing cloud configuration presence were verified without displaying configuration values. In-place installation and cold launch passed. Finish removed only wrapper-owned temporary logs. Fixtures use local synthetic images and injected clients, not live owner services.


APK SHA-256: `21105469564c04bc88e82a2a17d3b364282b97e3c003b7a01778c381f41c0508`. Package `app.stak.nativeapp.parity`, versionName `1.5.8`, versionCode `84`, original certificate `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430`.
