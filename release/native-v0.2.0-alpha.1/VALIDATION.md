# STAK Native 0.2.0 alpha — validation

Source commit: `96002dc3ac0cb42739cfd6af74081028a96d9f3c`.

APK artifact validation: https://github.com/ezatvnekvan-stack/STAK/actions/runs/39550855542

Independent push validation: https://github.com/ezatvnekvan-stack/STAK/actions/runs/39550850959

Both hosted runs passed. The release artifact run passed build, 22 unit tests, lint, all 10 first-pass Android checks (0 failures), and the second viewport capture. All 88 expected native screenshots have the reference viewport dimensions. The native motion MP4 was retained successfully.

## Local checks

- JDK 17 / Android SDK 35: debug APK, 22 unit tests (0 failures), lint (0 errors), and Android test-APK compilation passed.
- Original React/Capacitor frontend has no diff from source commit `c39fea06b3749408c4035854ab33adaf1e5a4260`.
- Original Rive asset is unchanged: SHA-256 `9b9c68b6319e5a186f99bb94ba5b3001f8a272cc3a4fd6a688e67b8bdf7310b4`.

## Evidence and limits

The emulator uses API 35 AOSP x86_64 with explicit display density/font scale. Screenshots crop system bars to 360×880 and 412×880 viewports. The 88 original/native screen comparisons cover 22 route/state captures in both themes. Pixel differences remain and include intentional design improvements. No pixel-perfect parity is claimed.

All 22 captured screen states were visually reviewed in dark at 412px and light at 360px for the preceding polish checkpoint. Account, source, title and sampled motion frames received closer review. The latest changes correct Home account status and a recording-file assertion.

The retained fixture motion video uses a manually advanced animation clock. It demonstrates poster navigation, switches and tabs; reduced-motion navigation is functionally asserted separately. It is not a physical-device or frame-rate benchmark. The native Rive test retains both themed illustrations and a reduced-motion static crop.

Provider responses and images in screenshots/video are test fixtures. They do not verify live credentialed TorBox/Jellyfin, supported Android versions, all accessibility scales or every original app feature. Remaining migration gaps are documented in `native-android/parity/README.md` in the source archive.

## APK identity and signing

The published APK is copied byte-for-byte from the successful CI artifact, retaining that artifact’s evaluation debug signature. `APK_PAYLOAD_VERIFICATION.json` records its exact SHA-256 and provenance. `APK_CERTIFICATE.txt`, `APK_IDENTITY.txt` and `SHA256SUMS.txt` identify the actual published file. No private signing key is included.

The debug certificate differs from the first Native alpha. Android cannot update that earlier alpha in place; existing Native users should retain an exported Native JSON backup before arranging a fresh Native installation. The original React/Capacitor STAK uses a separate package and its data remains intact.

This is an evaluation prerelease, not a production-signed Play Store release or a complete native replacement.

APK ZIP alignment passed with 16 KiB native-library page alignment. ARM64 shared libraries were inspected and their ELF LOAD segments use 0x4000 alignment. This is a binary inspection, not a 16 KiB device boot test.
